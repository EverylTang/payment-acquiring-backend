package com.example.payments.trade.service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record MerchantNotificationResponse(int statusCode, String body) {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public boolean isSuccessStatusCode() {
    return statusCode >= 200 && statusCode < 300;
  }

  /**
   * Uses an explicit acknowledgement when the merchant supplies one; otherwise preserves 2xx-only
   * compatibility.
   */
  public boolean isAcknowledged() {
    if (body == null || body.isBlank() || body.trim().equalsIgnoreCase("success")) return true;
    try {
      JsonNode response = OBJECT_MAPPER.readTree(body);
      if (!response.isObject()) return true;
      JsonNode success = response.get("success");
      if (success != null && success.isBoolean()) return success.booleanValue();
      JsonNode status = response.get("status");
      if (status != null && status.isTextual())
        return "success".equalsIgnoreCase(status.textValue());
      JsonNode code = response.get("code");
      if (code != null && code.isTextual()) return "SUCCESS".equalsIgnoreCase(code.textValue());
      return true;
    } catch (Exception ignored) {
      return true;
    }
  }
}
