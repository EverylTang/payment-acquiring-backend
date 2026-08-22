package com.example.payments.platform.service.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/merchants/{merchantId}")
public class AdminMerchantProfileController {
  private final JdbcClient jdbcClient;

  public AdminMerchantProfileController(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @GetMapping("/profile")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
  public ProfileResponse profile(@PathVariable String merchantId) {
    ensureMerchant(merchantId);
    return jdbcClient
        .sql(
            "SELECT merchant_id, legal_name, registered_country, industry, risk_level,"
                + " tax_identifier, created_at, updated_at FROM merchant_profile WHERE merchant_id"
                + " = :merchantId")
        .param("merchantId", merchantId)
        .query(ProfileResponse.class)
        .optional()
        .orElseGet(() -> new ProfileResponse(merchantId, "", "", null, "MEDIUM", null, null, null));
  }

  @PutMapping("/profile")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ProfileResponse updateProfile(
      @PathVariable String merchantId,
      @Valid @RequestBody ProfileRequest request,
      Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Timestamp.from(Instant.now());
    jdbcClient
        .sql(
            "INSERT INTO merchant_profile (merchant_id, legal_name, registered_country, industry,"
                + " risk_level, tax_identifier, created_at, updated_at) VALUES (:merchantId,"
                + " :legalName, :country, :industry, :risk, :taxId, :now, :now) ON DUPLICATE KEY"
                + " UPDATE legal_name = VALUES(legal_name), registered_country ="
                + " VALUES(registered_country), industry = VALUES(industry), risk_level ="
                + " VALUES(risk_level), tax_identifier = VALUES(tax_identifier), updated_at ="
                + " VALUES(updated_at)")
        .param("merchantId", merchantId)
        .param("legalName", request.legalName())
        .param("country", request.registeredCountry())
        .param("industry", request.industry())
        .param("risk", request.riskLevel())
        .param("taxId", request.taxIdentifier())
        .param("now", now)
        .update();
    audit(authentication.getName(), "UPDATE_PROFILE", merchantId);
    return profile(merchantId);
  }

  @GetMapping("/contacts")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
  public List<ContactResponse> contacts(@PathVariable String merchantId) {
    ensureMerchant(merchantId);
    return jdbcClient
        .sql(
            "SELECT id, merchant_id, contact_type, contact_name, email, phone, notify_enabled,"
                + " created_at, updated_at FROM merchant_contact WHERE merchant_id = :merchantId"
                + " ORDER BY id")
        .param("merchantId", merchantId)
        .query(ContactResponse.class)
        .list();
  }

  @PostMapping("/contacts")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ContactResponse createContact(
      @PathVariable String merchantId,
      @Valid @RequestBody ContactRequest request,
      Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Timestamp.from(Instant.now());
    jdbcClient
        .sql(
            "INSERT INTO merchant_contact (merchant_id, contact_type, contact_name, email, phone,"
                + " notify_enabled, created_at, updated_at) VALUES (:merchantId, :type, :name,"
                + " :email, :phone, :notify, :now, :now)")
        .param("merchantId", merchantId)
        .param("type", request.contactType())
        .param("name", request.contactName())
        .param("email", request.email())
        .param("phone", request.phone())
        .param("notify", request.notifyEnabled())
        .param("now", now)
        .update();
    audit(authentication.getName(), "CREATE_CONTACT", merchantId);
    return contacts(merchantId).stream()
        .filter(item -> item.contactType().equals(request.contactType()))
        .findFirst()
        .orElseThrow();
  }

  @PutMapping("/contacts/{contactId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ContactResponse updateContact(
      @PathVariable String merchantId,
      @PathVariable long contactId,
      @Valid @RequestBody ContactRequest request,
      Authentication authentication) {
    ensureMerchant(merchantId);
    var changed =
        jdbcClient
            .sql(
                "UPDATE merchant_contact SET contact_type = :type, contact_name = :name, email ="
                    + " :email, phone = :phone, notify_enabled = :notify, updated_at = :now WHERE"
                    + " id = :id AND merchant_id = :merchantId")
            .param("type", request.contactType())
            .param("name", request.contactName())
            .param("email", request.email())
            .param("phone", request.phone())
            .param("notify", request.notifyEnabled())
            .param("now", Timestamp.from(Instant.now()))
            .param("id", contactId)
            .param("merchantId", merchantId)
            .update();
    if (changed == 0) throw new IllegalArgumentException("联系人不存在: " + contactId);
    audit(authentication.getName(), "UPDATE_CONTACT", String.valueOf(contactId));
    return contacts(merchantId).stream()
        .filter(item -> item.id() == contactId)
        .findFirst()
        .orElseThrow();
  }

  @PutMapping("/callback-config")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public CallbackResponse updateCallback(
      @PathVariable String merchantId,
      @Valid @RequestBody CallbackRequest request,
      Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Timestamp.from(Instant.now());
    jdbcClient
        .sql(
            "INSERT INTO merchant_callback_config (merchant_id, callback_url, event_types, status,"
                + " created_at, updated_at) VALUES (:merchantId, :url, :events, :status, :now,"
                + " :now) ON DUPLICATE KEY UPDATE callback_url = VALUES(callback_url), event_types"
                + " = VALUES(event_types), status = VALUES(status), updated_at ="
                + " VALUES(updated_at)")
        .param("merchantId", merchantId)
        .param("url", request.callbackUrl())
        .param("events", request.eventTypesJson())
        .param("status", request.status())
        .param("now", now)
        .update();
    audit(authentication.getName(), "UPDATE_CALLBACK", merchantId);
    return callback(merchantId);
  }

  @GetMapping("/callback-config")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
  public CallbackResponse callback(@PathVariable String merchantId) {
    ensureMerchant(merchantId);
    return jdbcClient
        .sql(
            "SELECT merchant_id, callback_url, CAST(event_types AS CHAR) event_types, status,"
                + " created_at, updated_at FROM merchant_callback_config WHERE merchant_id ="
                + " :merchantId")
        .param("merchantId", merchantId)
        .query(CallbackResponse.class)
        .optional()
        .orElseGet(() -> new CallbackResponse(merchantId, "", "[]", "DISABLED", null, null));
  }

  @GetMapping("/credentials")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public List<CredentialResponse> credentials(@PathVariable String merchantId) {
    ensureMerchant(merchantId);
    return jdbcClient
        .sql(
            "SELECT credential_id, merchant_id, credential_type, secret_hint, status, created_at,"
                + " rotated_at, revoked_at FROM merchant_credential WHERE merchant_id = :merchantId"
                + " ORDER BY created_at DESC")
        .param("merchantId", merchantId)
        .query(CredentialResponse.class)
        .list();
  }

  @PostMapping("/credentials/rotate")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public RotatedCredential rotateCredential(
      @PathVariable String merchantId,
      @Valid @RequestBody CredentialRequest request,
      Authentication authentication) {
    ensureMerchant(merchantId);
    var secret =
        UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    var credentialId = UUID.randomUUID().toString();
    var now = Timestamp.from(Instant.now());
    jdbcClient
        .sql(
            "UPDATE merchant_credential SET status = 'REVOKED', revoked_at = :now WHERE merchant_id"
                + " = :merchantId AND credential_type = :type AND status = 'ACTIVE'")
        .param("merchantId", merchantId)
        .param("type", request.credentialType())
        .param("now", now)
        .update();
    jdbcClient
        .sql(
            "INSERT INTO merchant_credential (credential_id, merchant_id, credential_type,"
                + " secret_hash, secret_hint, status, created_at, rotated_at) VALUES (:id,"
                + " :merchantId, :type, :hash, :hint, 'ACTIVE', :now, :now)")
        .param("id", credentialId)
        .param("merchantId", merchantId)
        .param("type", request.credentialType())
        .param("hash", sha256(secret))
        .param("hint", secret.substring(0, 6) + "..." + secret.substring(secret.length() - 4))
        .param("now", now)
        .update();
    audit(authentication.getName(), "ROTATE_CREDENTIAL", merchantId);
    return new RotatedCredential(credentialId, request.credentialType(), secret, now.toInstant());
  }

  @PostMapping("/credentials/{credentialId}/revoke")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void revokeCredential(
      @PathVariable String merchantId,
      @PathVariable String credentialId,
      Authentication authentication) {
    ensureMerchant(merchantId);
    var changed =
        jdbcClient
            .sql(
                "UPDATE merchant_credential SET status = 'REVOKED', revoked_at = :now WHERE"
                    + " credential_id = :credentialId AND merchant_id = :merchantId AND status ="
                    + " 'ACTIVE'")
            .param("credentialId", credentialId)
            .param("merchantId", merchantId)
            .param("now", Timestamp.from(Instant.now()))
            .update();
    if (changed == 0) throw new IllegalArgumentException("有效凭证不存在: " + credentialId);
    audit(authentication.getName(), "REVOKE_CREDENTIAL", credentialId);
  }

  @DeleteMapping("/contacts/{contactId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void deleteContact(
      @PathVariable String merchantId,
      @PathVariable long contactId,
      Authentication authentication) {
    ensureMerchant(merchantId);
    jdbcClient
        .sql("DELETE FROM merchant_contact WHERE id = :id AND merchant_id = :merchantId")
        .param("id", contactId)
        .param("merchantId", merchantId)
        .update();
    audit(authentication.getName(), "DELETE_CONTACT", String.valueOf(contactId));
  }

  private void ensureMerchant(String merchantId) {
    if (jdbcClient
            .sql("SELECT COUNT(*) FROM merchant WHERE merchant_id = :merchantId")
            .param("merchantId", merchantId)
            .query(Long.class)
            .single()
        == 0) throw new IllegalArgumentException("商户不存在: " + merchantId);
  }

  private void audit(String operator, String action, String resourceId) {
    jdbcClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, :action, 'MERCHANT',"
                + " :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("resourceId", resourceId)
        .param("now", Timestamp.from(Instant.now()))
        .update();
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成凭证摘要", exception);
    }
  }

  public record ProfileRequest(
      @NotBlank String legalName,
      @NotBlank String registeredCountry,
      String industry,
      @Pattern(regexp = "LOW|MEDIUM|HIGH") String riskLevel,
      String taxIdentifier) {}

  public record ProfileResponse(
      String merchantId,
      String legalName,
      String registeredCountry,
      String industry,
      String riskLevel,
      String taxIdentifier,
      Instant createdAt,
      Instant updatedAt) {}

  public record ContactRequest(
      @NotBlank String contactType,
      @NotBlank String contactName,
      @Email String email,
      String phone,
      boolean notifyEnabled) {}

  public record ContactResponse(
      long id,
      String merchantId,
      String contactType,
      String contactName,
      String email,
      String phone,
      boolean notifyEnabled,
      Instant createdAt,
      Instant updatedAt) {}

  public record CallbackRequest(
      @NotBlank String callbackUrl,
      @NotBlank String eventTypesJson,
      @Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record CallbackResponse(
      String merchantId,
      String callbackUrl,
      String eventTypes,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  public record CredentialRequest(@Pattern(regexp = "API|WEBHOOK") String credentialType) {}

  public record CredentialResponse(
      String credentialId,
      String merchantId,
      String credentialType,
      String secretHint,
      String status,
      Instant createdAt,
      Instant rotatedAt,
      Instant revokedAt) {}

  public record RotatedCredential(
      String credentialId, String credentialType, String secret, Instant createdAt) {}
}
