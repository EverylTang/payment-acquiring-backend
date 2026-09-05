package com.example.payments.trade.service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class SimulatedChannelAdapter implements PaymentChannelAdapter {
  private final String signingSecret;

  public SimulatedChannelAdapter(
      @Value("${trade.channel.simulated.signing-secret:}") String signingSecret) {
    this.signingSecret = signingSecret;
  }

  @Override
  public String provider() {
    return "SIMULATED";
  }

  @Override
  public boolean supportsSignatureProfile(String signatureProfile) {
    try {
      ChannelRequestSigner.SignatureProfile.parse(signatureProfile);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  @Override
  public PaymentChannelResult createPayment(PaymentChannelRequest request) {
    String channelOrderId = "sim-" + request.attemptId();
    return new PaymentChannelResult(
        channelOrderId,
        "SUCCESS",
        "{\"status\":\"SUCCESS\"}",
        null,
        "https://simulated.local/pay/" + channelOrderId,
        null);
  }

  @Override
  public boolean supportsCancellation() {
    return true;
  }

  @Override
  public boolean supportsRefund() {
    return true;
  }

  @Override
  public PaymentChannelResult queryPayment(PaymentChannelQuery request) {
    String behavior = request.channelOrderId().contains("processing") ? "PROCESSING" : "SUCCESS";
    return new PaymentChannelResult(
        request.channelOrderId(), behavior, "{\"status\":\"" + behavior + "\"}", null, null, null);
  }

  @Override
  public PaymentChannelResult cancelPayment(PaymentChannelQuery request) {
    return new PaymentChannelResult(
        request.channelOrderId(), "CANCELED", "{\"status\":\"CANCELED\"}", null, null, null);
  }

  @Override
  public PaymentRefundResult refundPayment(PaymentRefundRequest request) {
    return new PaymentRefundResult(
        "sim-refund-" + request.refundId(), "SUCCESS", "{\"status\":\"SUCCESS\"}", null);
  }

  @Override
  public PaymentRefundCallback verifyRefundCallback(PaymentRefundCallbackRequest request) {
    long now = System.currentTimeMillis() / 1000;
    if (Math.abs(now - request.timestamp()) > 300
        || request.nonce() == null
        || request.nonce().isBlank()) throw new IllegalArgumentException("refund callback expired");
    if (!sign(
            request.timestamp() + "." + request.nonce() + "." + request.rawPayload(),
            signingSecret(request.runtime()))
        .equalsIgnoreCase(request.signature()))
      throw new IllegalArgumentException("invalid refund callback signature");
    String[] fields = request.rawPayload().split("\\|", -1);
    if (fields.length != 2 || fields[0].isBlank())
      throw new IllegalArgumentException("invalid refund callback payload");
    return new PaymentRefundCallback(
        request.callbackId(), fields[0], fields[1].toUpperCase(), request.rawPayload());
  }

  @Override
  public PaymentCallback verifyCallback(PaymentCallbackRequest request) {
    if (request.callbackId() == null || request.callbackId().isBlank()) {
      throw new IllegalArgumentException("callback id is required");
    }
    if (!sign(request.rawPayload(), signingSecret(request.runtime()))
        .equalsIgnoreCase(request.signature())) {
      throw new IllegalArgumentException("invalid callback signature");
    }
    String[] fields = request.rawPayload().split("\\|", -1);
    if (fields.length != 3 || fields[1].isBlank() || fields[2].isBlank()) {
      throw new IllegalArgumentException("invalid callback payload");
    }
    return new PaymentCallback(
        request.callbackId(), fields[0], fields[1].toUpperCase(), request.rawPayload());
  }

  @Override
  public String callbackChannelOrderId(String rawPayload) {
    String[] fields = rawPayload.split("\\|", -1);
    if (fields.length != 3 || fields[0].isBlank()) {
      throw new IllegalArgumentException("invalid callback payload");
    }
    return fields[0];
  }

  public String sign(String rawPayload) {
    return sign(rawPayload, signingSecret);
  }

  private String sign(String rawPayload, String secret) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest((secret + "." + rawPayload).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String signingSecret(ChannelRuntimeContext runtime) {
    var configured =
        runtime == null
            ? java.util.Optional.<String>empty()
            : runtime.secret("callbackVerifyKey").or(() -> runtime.secret("requestSigningKey"));
    return configured
        .or(() -> java.util.Optional.ofNullable(signingSecret).filter(value -> !value.isBlank()))
        .orElseThrow(() -> new IllegalStateException("渠道签名密钥未配置"));
  }
}
