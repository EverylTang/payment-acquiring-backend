package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.ConfigurationHealthMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigurationHealthService {
  private final ConfigurationHealthMapper mapper;

  public Map<String, Object> health(String id) {
    var status = mapper.status(id);
    return Map.of(
        "channelId",
        id,
        "status",
        "ACTIVE".equals(status) ? "UP" : "DOWN",
        "checkedAt",
        System.currentTimeMillis());
  }
}
