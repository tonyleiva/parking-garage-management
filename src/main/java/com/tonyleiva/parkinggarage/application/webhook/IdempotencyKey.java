package com.tonyleiva.parkinggarage.application.webhook;

public record IdempotencyKey(String logicalKey, byte[] hash) {
  public IdempotencyKey {
    hash = hash.clone();
  }

  @Override
  public byte[] hash() { return hash.clone(); }
}
