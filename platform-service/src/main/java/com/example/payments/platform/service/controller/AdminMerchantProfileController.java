package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.admin.MerchantProfileAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/merchants/{merchantId}")
@RequiredArgsConstructor
public class AdminMerchantProfileController {
  private final MerchantProfileAdminService service;

  @GetMapping("/profile")
  public MerchantProfileAdminService.ProfileResponse profile(@PathVariable String merchantId) {
    return service.profile(merchantId);
  }

  @PutMapping("/profile")
  public MerchantProfileAdminService.ProfileResponse updateProfile(
      @PathVariable String merchantId,
      @RequestBody MerchantProfileAdminService.ProfileRequest r,
      Authentication a) {
    return service.updateProfile(merchantId, r, a);
  }

  @GetMapping("/contacts")
  public java.util.List<MerchantProfileAdminService.ContactResponse> contacts(
      @PathVariable String merchantId) {
    return service.contacts(merchantId);
  }

  @PostMapping("/contacts")
  public MerchantProfileAdminService.ContactResponse createContact(
      @PathVariable String merchantId,
      @RequestBody MerchantProfileAdminService.ContactRequest r,
      Authentication a) {
    return service.createContact(merchantId, r, a);
  }

  @PutMapping("/contacts/{contactId}")
  public MerchantProfileAdminService.ContactResponse updateContact(
      @PathVariable String merchantId,
      @PathVariable long contactId,
      @RequestBody MerchantProfileAdminService.ContactRequest r,
      Authentication a) {
    return service.updateContact(merchantId, contactId, r, a);
  }

  @PutMapping("/callback-config")
  public MerchantProfileAdminService.CallbackResponse updateCallback(
      @PathVariable String merchantId,
      @RequestBody MerchantProfileAdminService.CallbackRequest r,
      Authentication a) {
    return service.updateCallback(merchantId, r, a);
  }

  @GetMapping("/callback-config")
  public MerchantProfileAdminService.CallbackResponse callback(@PathVariable String merchantId) {
    return service.callback(merchantId);
  }

  @GetMapping("/credentials")
  public java.util.List<MerchantProfileAdminService.CredentialResponse> credentials(
      @PathVariable String merchantId) {
    return service.credentials(merchantId);
  }

  @PostMapping("/credentials/rotate")
  public MerchantProfileAdminService.RotatedCredential rotateCredential(
      @PathVariable String merchantId,
      @RequestBody MerchantProfileAdminService.CredentialRequest r,
      Authentication a) {
    return service.rotateCredential(merchantId, r, a);
  }

  @PostMapping("/credentials/{credentialId}/revoke")
  public void revokeCredential(
      @PathVariable String merchantId, @PathVariable String credentialId, Authentication a) {
    service.revokeCredential(merchantId, credentialId, a);
  }

  @DeleteMapping("/contacts/{contactId}")
  public void deleteContact(
      @PathVariable String merchantId, @PathVariable long contactId, Authentication a) {
    service.deleteContact(merchantId, contactId, a);
  }
}
