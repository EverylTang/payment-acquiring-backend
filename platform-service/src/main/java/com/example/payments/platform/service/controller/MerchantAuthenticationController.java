package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.MerchantApiAuthenticationService;
import com.example.payments.platform.service.service.MerchantNotificationSigningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/merchant-authentication")
@RequiredArgsConstructor
public class MerchantAuthenticationController {
  private final MerchantApiAuthenticationService authenticationService;
  private final MerchantNotificationSigningService notificationSigningService;

  @PostMapping("/verify")
  public Map<String, String> verify(@Valid @RequestBody VerifyRequest request) {
    String merchantId =
        authenticationService.authenticate(
            new MerchantApiAuthenticationService.AuthenticationRequest(
                request.keyId(),
                request.timestamp(),
                request.nonce(),
                request.signature(),
                request.sourceIp(),
                request.canonicalPayload()));
    return Map.of("merchantId", merchantId, "keyId", request.keyId());
  }

  @PostMapping("/notifications/sign")
  public MerchantNotificationSigningService.SignedNotification signNotification(
      @Valid @RequestBody SignNotificationRequest request) {
    return notificationSigningService.sign(
        request.merchantId(),
        request.body(),
        request.nonce(),
        java.time.Instant.ofEpochSecond(request.timestamp()));
  }

  public record VerifyRequest(
      @NotBlank String keyId,
      @Positive long timestamp,
      @NotBlank String nonce,
      @NotBlank String signature,
      @NotBlank String sourceIp,
      @NotBlank String canonicalPayload) {}

  public record SignNotificationRequest(
      @NotBlank String merchantId,
      @NotBlank String body,
      @NotBlank String nonce,
      @Positive long timestamp) {}
}
