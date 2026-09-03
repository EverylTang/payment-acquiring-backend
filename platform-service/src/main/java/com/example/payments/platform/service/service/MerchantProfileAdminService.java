package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MerchantCallbackConfigFullMapper;
import com.example.payments.platform.service.mapper.MerchantContactFullMapper;
import com.example.payments.platform.service.mapper.MerchantCredentialFullMapper;
import com.example.payments.platform.service.mapper.MerchantProfileMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantProfileAdminService {
  private final PlatformDataService mybatisClient;
  private final MerchantProfileMapper merchantProfileMapper;
  private final MerchantContactFullMapper merchantContactFullMapper;
  private final MerchantCallbackConfigFullMapper merchantCallbackConfigFullMapper;
  private final MerchantCredentialFullMapper merchantCredentialFullMapper;

  public ProfileResponse profile(String merchantId) {
    ensureMerchant(merchantId);
    var model = merchantProfileMapper.selectByMerchantId(merchantId);
    if (model == null) {
      return new ProfileResponse(merchantId, "", "", null, "MEDIUM", null, null, null);
    }
    return new ProfileResponse(
        model.merchantId(),
        model.legalName(),
        model.registeredCountry(),
        model.industry(),
        model.riskLevel(),
        model.taxIdentifier(),
        model.createdAt(),
        model.updatedAt());
  }

  @Transactional
  public ProfileResponse updateProfile(
      String merchantId, ProfileRequest request, Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Instant.now();
    merchantProfileMapper.upsert(
        merchantId,
        request.legalName(),
        request.registeredCountry(),
        request.industry(),
        request.riskLevel(),
        request.taxIdentifier(),
        now);
    audit(authentication.getName(), "UPDATE_PROFILE", merchantId);
    return profile(merchantId);
  }

  public List<ContactResponse> contacts(String merchantId) {
    ensureMerchant(merchantId);
    return merchantContactFullMapper.selectByMerchantId(merchantId).stream()
        .map(
            m ->
                new ContactResponse(
                    m.id(),
                    m.merchantId(),
                    m.contactType(),
                    m.contactName(),
                    m.email(),
                    m.phone(),
                    m.notifyEnabled(),
                    m.createdAt(),
                    m.updatedAt()))
        .toList();
  }

  @Transactional
  public ContactResponse createContact(
      String merchantId, ContactRequest request, Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Instant.now();
    merchantContactFullMapper.insert(
        merchantId,
        request.contactType(),
        request.contactName(),
        request.email(),
        request.phone(),
        request.notifyEnabled(),
        now);
    audit(authentication.getName(), "CREATE_CONTACT", merchantId);
    return contacts(merchantId).stream()
        .filter(item -> item.contactType().equals(request.contactType()))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public ContactResponse updateContact(
      String merchantId, long contactId, ContactRequest request, Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Instant.now();
    var changed =
        merchantContactFullMapper.update(
            contactId,
            merchantId,
            request.contactType(),
            request.contactName(),
            request.email(),
            request.phone(),
            request.notifyEnabled(),
            now);
    if (changed == 0) throw new IllegalArgumentException("联系人不存在: " + contactId);
    audit(authentication.getName(), "UPDATE_CONTACT", String.valueOf(contactId));
    return contacts(merchantId).stream()
        .filter(item -> item.id() == contactId)
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public CallbackResponse updateCallback(
      String merchantId, CallbackRequest request, Authentication authentication) {
    ensureMerchant(merchantId);
    var now = Instant.now();
    merchantCallbackConfigFullMapper.upsert(
        merchantId, request.callbackUrl(), request.eventTypesJson(), request.status(), now);
    audit(authentication.getName(), "UPDATE_CALLBACK", merchantId);
    return callback(merchantId);
  }

  public CallbackResponse callback(String merchantId) {
    ensureMerchant(merchantId);
    var model = merchantCallbackConfigFullMapper.selectByMerchantId(merchantId);
    if (model == null) {
      return new CallbackResponse(merchantId, "", "[]", "DISABLED", null, null);
    }
    return new CallbackResponse(
        model.merchantId(),
        model.callbackUrl(),
        model.eventTypes(),
        model.status(),
        model.createdAt(),
        model.updatedAt());
  }

  public List<CredentialResponse> credentials(String merchantId) {
    ensureMerchant(merchantId);
    return merchantCredentialFullMapper.selectByMerchantId(merchantId).stream()
        .map(
            m ->
                new CredentialResponse(
                    m.credentialId(),
                    m.merchantId(),
                    m.credentialType(),
                    m.secretHint(),
                    m.status(),
                    m.createdAt(),
                    m.rotatedAt(),
                    m.revokedAt()))
        .toList();
  }

  @Transactional
  public RotatedCredential rotateCredential(
      String merchantId, CredentialRequest request, Authentication authentication) {
    ensureMerchant(merchantId);
    var secret =
        UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    var credentialId = UUID.randomUUID().toString();
    var now = Instant.now();
    merchantCredentialFullMapper.revokeActiveByType(merchantId, request.credentialType(), now);
    var secretHint = secret.substring(0, 6) + "..." + secret.substring(secret.length() - 4);
    merchantCredentialFullMapper.insert(
        credentialId, merchantId, request.credentialType(), sha256(secret), secretHint, now);
    audit(authentication.getName(), "ROTATE_CREDENTIAL", merchantId);
    return new RotatedCredential(credentialId, request.credentialType(), secret, now);
  }

  @Transactional
  public void revokeCredential(
      String merchantId, String credentialId, Authentication authentication) {
    ensureMerchant(merchantId);
    var changed = merchantCredentialFullMapper.revokeById(credentialId, merchantId, Instant.now());
    if (changed == 0) throw new IllegalArgumentException("有效凭证不存在: " + credentialId);
    audit(authentication.getName(), "REVOKE_CREDENTIAL", credentialId);
  }

  @Transactional
  public void deleteContact(String merchantId, long contactId, Authentication authentication) {
    ensureMerchant(merchantId);
    merchantContactFullMapper.deleteById(contactId, merchantId);
    audit(authentication.getName(), "DELETE_CONTACT", String.valueOf(contactId));
  }

  private void ensureMerchant(String merchantId) {
    if (mybatisClient
            .sql("SELECT COUNT(*) FROM merchant WHERE merchant_id = :merchantId")
            .param("merchantId", merchantId)
            .query(Long.class)
            .single()
        == 0) throw new IllegalArgumentException("商户不存在: " + merchantId);
  }

  private void audit(String operator, String action, String resourceId) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, :action, 'MERCHANT',"
                + " :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("resourceId", resourceId)
        .param("now", Instant.now())
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
