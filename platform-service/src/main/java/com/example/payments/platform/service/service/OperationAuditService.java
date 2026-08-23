package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.OperationAuditMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationAuditService {
  private final OperationAuditMapper mapper;
  private final ObjectMapper objectMapper;
  public void record(String operator,String action,String resourceType,String resourceId,Object payload){
    mapper.insertAudit(UUID.randomUUID().toString(),operator,action,resourceType,resourceId,json(payload),Instant.now());
  }
  private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("审计摘要无法序列化",e);}}
}
