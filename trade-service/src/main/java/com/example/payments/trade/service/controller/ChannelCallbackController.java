package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.ChannelAdapterRegistry;
import com.example.payments.trade.service.service.PaymentAttemptService;
import com.example.payments.trade.service.service.PlatformChannelConfigurationClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider callback endpoint. Provider-specific parsing and verification remain inside adapters.
 */
@RestController
@RequestMapping("/api/v1/payments/channels")
@RequiredArgsConstructor
public class ChannelCallbackController {
  private final PaymentAttemptService paymentAttemptService;
  private final PlatformChannelConfigurationClient channelConfiguration;
  private final ChannelAdapterRegistry channelAdapters;

  @PostMapping(
      value = "/{channelId}/callback",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> callback(
      @PathVariable String channelId, @RequestBody String rawPayload) {
    var runtime = channelConfiguration.resolve(channelId);
    var adapter = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
    // The provider document has no event ID. A body hash makes retry delivery idempotent.
    String callbackId = sha256(channelId + "." + rawPayload);
    adapter.callbackChannelOrderId(rawPayload);
    paymentAttemptService.callback(channelId, rawPayload, "embedded", callbackId);
    String acknowledgement = runtime.setting("callbackSuccessResponse");
    return ResponseEntity.ok(acknowledgement.isBlank() ? "SUCCESS" : acknowledgement);
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成渠道回调幂等键", exception);
    }
  }
}
