package com.tonyleiva.parkinggarage.application.revenue;

import java.math.BigDecimal;
import java.time.Instant;

public record RevenueResult(BigDecimal amount, String currency, Instant timestamp) {}
