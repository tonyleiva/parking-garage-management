package com.tonyleiva.parkinggarage.application.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

  @Test
  void shouldCompareHashByContent() {
    var first = new IdempotencyKey("request-1", new byte[] {1, 2, 3});
    var sameContent = new IdempotencyKey("request-1", new byte[] {1, 2, 3});
    var differentContent = new IdempotencyKey("request-1", new byte[] {1, 2, 4});
    var differentLogicalKey = new IdempotencyKey("request-2", new byte[] {1, 2, 3});

    assertThat(first).isEqualTo(sameContent).hasSameHashCodeAs(sameContent);
    assertThat(first).isNotEqualTo(differentContent).isNotEqualTo(differentLogicalKey);
  }

  @Test
  void shouldRenderHashAsHexadecimal() {
    var key = new IdempotencyKey("request-1", new byte[] {0, 15, -1});

    assertThat(key.toString())
        .isEqualTo("IdempotencyKey[logicalKey=request-1, hash=000fff]")
        .doesNotContain("[B@");
  }

  @Test
  void shouldDefensivelyCopyHashOnConstruction() {
    byte[] original = {1, 2, 3};
    var key = new IdempotencyKey("request-1", original);

    original[0] = 9;

    assertThat(key.hash()).containsExactly(1, 2, 3);
  }

  @Test
  void shouldDefensivelyCopyHashOnAccess() {
    var key = new IdempotencyKey("request-1", new byte[] {1, 2, 3});

    byte[] returnedHash = key.hash();
    returnedHash[0] = 9;

    assertThat(key.hash()).containsExactly(1, 2, 3);
  }
}
