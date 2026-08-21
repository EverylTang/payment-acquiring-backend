package com.example.payments.trade.service.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedChannelAdapter implements PaymentChannelAdapter {
  private final String signingSecret;

  public SimulatedChannelAdapter(@Value("${trade.channel.simulated.signing-secret:local-simulated-channel-secret}") String signingSecret) {
    this.signingSecret = signingSecret;
  }

  @Override
  public PaymentChannelResult createPayment(PaymentChannelRequest request) {
    String channelOrderId = "sim-" + request.attemptId();
    String behavior = request.behavior() == null || request.behavior().isBlank() ? "SUCCESS" : request.behavior().toUpperCase();
    return switch (behavior) {
      case "FAILED" -> new PaymentChannelResult(channelOrderId, "FAILED", "{\"status\":\"FAILED\"}", "SIMULATED_FAILURE", null);
      case "PROCESSING", "TIMEOUT" -> new PaymentChannelResult(channelOrderId, behavior, "{\"status\":\"" + behavior + "\"}", null, "https://simulated.local/pay/" + channelOrderId);
      default -> new PaymentChannelResult(channelOrderId, "SUCCESS", "{\"status\":\"SUCCESS\"}", null, "https://simulated.local/pay/" + channelOrderId);
    };
  }

  @Override
  public PaymentChannelResult queryPayment(PaymentChannelQuery request) {
    String behavior = request.channelOrderId().contains("processing") ? "PROCESSING" : "SUCCESS";
    return new PaymentChannelResult(request.channelOrderId(), behavior, "{\"status\":\"" + behavior + "\"}", null, null);
  }

  @Override
  public PaymentChannelResult cancelPayment(PaymentChannelQuery request) {
    return new PaymentChannelResult(request.channelOrderId(), "CANCELED", "{\"status\":\"CANCELED\"}", null, null);
  }

  @Override
  public PaymentCallback verifyCallback(PaymentCallbackRequest request) {
    if (request.callbackId() == null || request.callbackId().isBlank()) {
      throw new IllegalArgumentException("callback id is required");
    }
    if (!sign(request.rawPayload()).equalsIgnoreCase(request.signature())) {
      throw new IllegalArgumentException("invalid callback signature");
    }
    String[] fields = request.rawPayload().split("\\|", -1);
    if (fields.length != 3 || fields[1].isBlank() || fields[2].isBlank()) {
      throw new IllegalArgumentException("invalid callback payload");
    }
    return new PaymentCallback(request.callbackId(), fields[0], fields[1].toUpperCase(), request.rawPayload());
  }

  public String sign(String rawPayload) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest((signingSecret + "." + rawPayload).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
