package com.example.payments.trade.service.service;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MerchantCallbackUrlPolicy {
  private final boolean allowHttp;

  public MerchantCallbackUrlPolicy(Environment environment) {
    allowHttp = Arrays.stream(environment.getActiveProfiles())
        .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
  }

  MerchantCallbackUrlPolicy(boolean allowHttp) {
    this.allowHttp = allowHttp;
  }

  public String validate(String value, String field) {
    if (value == null || value.isBlank()) return null;
    final URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw invalid(field);
    }
    if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
        || (!"https".equalsIgnoreCase(uri.getScheme())
            && !(allowHttp && "http".equalsIgnoreCase(uri.getScheme())))) {
      throw invalid(field);
    }
    try {
      for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
        if (blocked(address)) throw invalid(field);
      }
    } catch (java.net.UnknownHostException exception) {
      throw invalid(field);
    }
    return uri.toASCIIString();
  }

  private static boolean blocked(InetAddress address) {
    byte[] bytes = address.getAddress();
    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
        || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
    if (bytes.length == 4) {
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127);
    }
    return (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
  }

  private static ResponseStatusException invalid(String field) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is not a permitted callback URL");
  }
}
