package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.admin.MerchantProfileAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/merchants/{merchantId}")
@RequiredArgsConstructor
public class AdminMerchantProfileController {
  private final MerchantProfileAdminService service;

  @GetMapping("/profile")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
  public MerchantProfileAdminService.ProfileResponse profile(@PathVariable String merchantId) {
    return service.profile(merchantId);
  }

  @PutMapping("/profile")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public MerchantProfileAdminService.ProfileResponse updateProfile(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.ProfileRequest r,
      Authentication a) {
    return service.updateProfile(merchantId, r, a);
  }

  @GetMapping("/contacts")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
  public java.util.List<MerchantProfileAdminService.ContactResponse> contacts(
      @PathVariable String merchantId) {
    return service.contacts(merchantId);
  }

  @PostMapping("/contacts")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public MerchantProfileAdminService.ContactResponse createContact(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.ContactRequest r,
      Authentication a) {
    return service.createContact(merchantId, r, a);
  }

  @PutMapping("/contacts/{contactId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public MerchantProfileAdminService.ContactResponse updateContact(
      @PathVariable String merchantId,
      @PathVariable long contactId,
      @Valid @RequestBody MerchantProfileAdminService.ContactRequest r,
      Authentication a) {
    return service.updateContact(merchantId, contactId, r, a);
  }

  @PutMapping("/callback-config")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public MerchantProfileAdminService.CallbackResponse updateCallback(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.CallbackRequest r,
      Authentication a) {
    return service.updateCallback(merchantId, r, a);
  }

  @GetMapping("/callback-config")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
  public MerchantProfileAdminService.CallbackResponse callback(@PathVariable String merchantId) {
    return service.callback(merchantId);
  }

  @GetMapping("/credentials")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public java.util.List<MerchantProfileAdminService.CredentialResponse> credentials(
      @PathVariable String merchantId) {
    return service.credentials(merchantId);
  }

  @PostMapping("/credentials/rotate")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public MerchantProfileAdminService.RotatedCredential rotateCredential(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.CredentialRequest r,
      Authentication a) {
    return service.rotateCredential(merchantId, r, a);
  }

  @PostMapping("/credentials/{credentialId}/revoke")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public void revokeCredential(
      @PathVariable String merchantId, @PathVariable String credentialId, Authentication a) {
    service.revokeCredential(merchantId, credentialId, a);
  }

  @DeleteMapping("/contacts/{contactId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public void deleteContact(
      @PathVariable String merchantId, @PathVariable long contactId, Authentication a) {
    service.deleteContact(merchantId, contactId, a);
  }
}
