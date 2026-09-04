package com.example.payments.trade.service.service;

public interface ChannelSecretResolver {
  String resolve(ChannelCredentialReference reference);
}
