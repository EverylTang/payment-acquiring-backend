package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.MerchantProfileAdminService;
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
  @PreAuthorize("hasAuthority('merchant:profile')")
  public MerchantProfileAdminService.ProfileResponse profile(@PathVariable String merchantId) {
    return service.profile(merchantId);
  }

  @PutMapping("/profile")
  @PreAuthorize("hasAuthority('merchant:profile:update')")
  public MerchantProfileAdminService.ProfileResponse updateProfile(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.ProfileRequest r,
      Authentication a) {
    return service.updateProfile(merchantId, r, a);
  }

  @GetMapping("/contacts")
  @PreAuthorize("hasAuthority('merchant:contact:list')")
  public java.util.List<MerchantProfileAdminService.ContactResponse> contacts(
      @PathVariable String merchantId) {
    return service.contacts(merchantId);
  }

  @PostMapping("/contacts")
  @PreAuthorize("hasAuthority('merchant:contact:update')")
  public MerchantProfileAdminService.ContactResponse createContact(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.ContactRequest r,
      Authentication a) {
    return service.createContact(merchantId, r, a);
  }

  @PutMapping("/contacts/{contactId}")
  @PreAuthorize("hasAuthority('merchant:contact:update')")
  public MerchantProfileAdminService.ContactResponse updateContact(
      @PathVariable String merchantId,
      @PathVariable long contactId,
      @Valid @RequestBody MerchantProfileAdminService.ContactRequest r,
      Authentication a) {
    return service.updateContact(merchantId, contactId, r, a);
  }

  @PutMapping("/callback-config")
  @PreAuthorize("hasAuthority('merchant:callback:update')")
  public MerchantProfileAdminService.CallbackResponse updateCallback(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.CallbackRequest r,
      Authentication a) {
    return service.updateCallback(merchantId, r, a);
  }

  @GetMapping("/callback-config")
  @PreAuthorize("hasAuthority('merchant:callback:list')")
  public MerchantProfileAdminService.CallbackResponse callback(@PathVariable String merchantId) {
    return service.callback(merchantId);
  }

  @GetMapping("/credentials")
  @PreAuthorize("hasAuthority('merchant:credential:list')")
  public java.util.List<MerchantProfileAdminService.CredentialResponse> credentials(
      @PathVariable String merchantId) {
    return service.credentials(merchantId);
  }

  @PostMapping("/credentials/rotate")
  @PreAuthorize("hasAuthority('merchant:credential:rotate')")
  public MerchantProfileAdminService.RotatedCredential rotateCredential(
      @PathVariable String merchantId,
      @Valid @RequestBody MerchantProfileAdminService.CredentialRequest r,
      Authentication a) {
    return service.rotateCredential(merchantId, r, a);
  }

  @PostMapping("/credentials/{credentialId}/revoke")
  @PreAuthorize("hasAuthority('merchant:credential:revoke')")
  public void revokeCredential(
      @PathVariable String merchantId, @PathVariable String credentialId, Authentication a) {
    service.revokeCredential(merchantId, credentialId, a);
  }

  @DeleteMapping("/contacts/{contactId}")
  @PreAuthorize("hasAuthority('merchant:contact:update')")
  public void deleteContact(
      @PathVariable String merchantId, @PathVariable long contactId, Authentication a) {
    service.deleteContact(merchantId, contactId, a);
  }
}
