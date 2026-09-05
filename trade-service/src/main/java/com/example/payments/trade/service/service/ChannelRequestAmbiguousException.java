package com.example.payments.trade.service.service;

/** The provider may have accepted the request although no definitive response was received. */
public class ChannelRequestAmbiguousException extends RuntimeException {
  public ChannelRequestAmbiguousException(String message, Throwable cause) {
    super(message, cause);
  }
}
