package com.tonyleiva.parkinggarage.application.webhook;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public record IdempotencyKey(String logicalKey, byte[] hash) {
  public IdempotencyKey {
    hash = hash.clone();
  }

  @Override
  public byte[] hash() { return hash.clone(); }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof IdempotencyKey that)) {
      return false;
    }
    return Objects.equals(logicalKey, that.logicalKey) && Arrays.equals(hash, that.hash);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(logicalKey) + Arrays.hashCode(hash);
  }

  @Override
  public String toString() {
    return "IdempotencyKey[logicalKey=" + logicalKey
        + ", hash=" + HexFormat.of().formatHex(hash) + "]";
  }
}
