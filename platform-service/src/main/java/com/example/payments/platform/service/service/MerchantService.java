package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MerchantMapper;
import com.example.payments.platform.service.model.MerchantModel;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {
  private final MerchantMapper mapper;
  private final AdminMerchantAccessService access;

  public Page list(int page, int pageSize, Authentication auth) {
    int safePage = Math.max(page, 1), safeSize = Math.min(Math.max(pageSize, 1), 100);
    String username = auth.getName();
    boolean all = access.hasAllScope(username);
    return new Page(
        mapper.selectVisible(username, all, safeSize, (safePage - 1) * safeSize),
        safePage,
        safeSize,
        mapper.countVisible(username, all));
  }

  public MerchantModel detail(String id, Authentication auth) {
    String username = auth.getName();
    MerchantModel value = mapper.selectVisibleById(id, username, access.hasAllScope(username));
    if (value == null) throw new IllegalArgumentException("商户不存在或无权访问");
    return value;
  }

  @Transactional
  public MerchantModel create(String requestedId, String name, Authentication auth) {
    var now = Instant.now();
    String merchantId = requestedId == null ? "" : requestedId.trim();
    if (merchantId.isEmpty()) {
      mapper.insertMerchantWithGeneratedId(name, now);
      long databaseId = mapper.selectLastInsertId();
      merchantId = "mch_" + databaseId;
      mapper.updateMerchantId(databaseId, merchantId);
    } else {
      mapper.insertMerchant(merchantId, name, now);
    }
    return detail(merchantId, auth);
  }

  @Transactional
  public MerchantModel update(String id, String name, Authentication auth) {
    access.assertAllowed(auth, id);
    mapper.updateMerchant(id, name, Instant.now());
    return detail(id, auth);
  }

  @Transactional
  public MerchantModel changeStatus(String id, String status, Authentication auth) {
    access.assertAllowed(auth, id);
    mapper.updateStatus(id, status, Instant.now());
    return detail(id, auth);
  }

  public record Page(java.util.List<MerchantModel> items, int page, int pageSize, long total) {}
}
