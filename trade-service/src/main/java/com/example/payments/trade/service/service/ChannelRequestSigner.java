package com.example.payments.trade.service.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Creates a channel request signature without exposing the resolved credential to adapters. */
@Component
public class ChannelRequestSigner {
  private static final String DEFAULT_SIGNATURE_FIELD = "sign";

  public ChannelRequestSignature sign(
      ChannelRuntimeContext runtime, Map<String, String> requestFields) {
    var profile = SignatureProfile.parse(runtime.signatureProfile());
    var fields = signedFields(runtime, requestFields);
    if (profile == SignatureProfile.NONE) {
      return new ChannelRequestSignature(fields, signatureField(runtime), "");
    }
    var secretRole = credentialSetting(runtime, "signatureSecretRole", "requestSigningKey");
    var secret = runtime.secret(secretRole).orElseThrow(() -> missingSecret(secretRole));
    return new ChannelRequestSignature(
        fields, signatureField(runtime), profile.sign(canonical(fields), secret));
  }

  private Map<String, String> signedFields(
      ChannelRuntimeContext runtime, Map<String, String> requestFields) {
    var selected = setting(runtime, "signatureFields", "");
    var fields = new TreeMap<String, String>();
    if (selected.isBlank()) {
      requestFields.forEach(
          (key, value) -> {
            if (value != null && !value.isBlank()) fields.put(key, value);
          });
    } else {
      for (var key : selected.split(",")) {
        var field = key.trim();
        var value = requestFields.get(field);
        if (field.isBlank() || value == null || value.isBlank()) {
          throw new IllegalArgumentException("渠道签名字段未提供: " + field);
        }
        fields.put(field, value);
      }
    }
    if (fields.isEmpty()) throw new IllegalArgumentException("渠道签名字段不能为空");
    return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  private String signatureField(ChannelRuntimeContext runtime) {
    return setting(runtime, "signatureFieldName", DEFAULT_SIGNATURE_FIELD);
  }

  private String setting(ChannelRuntimeContext runtime, String key, String defaultValue) {
    var value = runtime.settings().get(key);
    return value == null || String.valueOf(value).isBlank()
        ? defaultValue
        : String.valueOf(value).trim();
  }

  private String credentialSetting(ChannelRuntimeContext runtime, String key, String defaultValue) {
    var value = runtime.credentials().get(key);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private String canonical(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + "&" + right)
        .orElseThrow();
  }

  private IllegalStateException missingSecret(String role) {
    return new IllegalStateException("渠道签名密钥未配置: " + role);
  }

  public record ChannelRequestSignature(
      Map<String, String> signedFields, String fieldName, String value) {}

  enum SignatureProfile {
    NONE {
      @Override
      String sign(String canonical, String secret) {
        return "";
      }
    },
    DEFAULT {
      @Override
      String sign(String canonical, String secret) {
        return digest("SHA-256", secret + "." + canonical);
      }
    },
    SIMULATED_SHA256_PREFIX_V1 {
      @Override
      String sign(String canonical, String secret) {
        return digest("SHA-256", secret + "." + canonical);
      }
    },
    MD5_KEY_SUFFIX_V1 {
      @Override
      String sign(String canonical, String secret) {
        return digest("MD5", canonical + "&key=" + secret);
      }
    },
    SHA256_KEY_SUFFIX_V1 {
      @Override
      String sign(String canonical, String secret) {
        return digest("SHA-256", canonical + "&key=" + secret);
      }
    },
    HMAC_SHA256_V1 {
      @Override
      String sign(String canonical, String secret) {
        return hmac("HmacSHA256", canonical, secret);
      }
    },
    HMAC_SHA512_V1 {
      @Override
      String sign(String canonical, String secret) {
        return hmac("HmacSHA512", canonical, secret);
      }
    },
    RSA_SHA256_V1 {
      @Override
      String sign(String canonical, String secret) {
        try {
          var key =
              KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key(secret)));
          var signature = Signature.getInstance("SHA256withRSA");
          signature.initSign(key);
          signature.update(canonical.getBytes(StandardCharsets.UTF_8));
          return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
          throw new IllegalArgumentException("RSA 签名私钥无效", exception);
        }
      }
    };

    abstract String sign(String canonical, String secret);

    static SignatureProfile parse(String profile) {
      try {
        return valueOf(profile.toUpperCase(Locale.ROOT));
      } catch (RuntimeException exception) {
        throw new IllegalArgumentException("不支持的渠道签名方案: " + profile, exception);
      }
    }

    static String digest(String algorithm, String value) {
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance(algorithm)
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
        throw new IllegalStateException("签名算法不可用: " + algorithm, exception);
      }
    }

    static String hmac(String algorithm, String value, String secret) {
      try {
        var mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
        throw new IllegalStateException("签名算法不可用: " + algorithm, exception);
      }
    }

    static byte[] key(String value) {
      var encoded =
          value
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      return Base64.getDecoder().decode(encoded);
    }
  }
}
