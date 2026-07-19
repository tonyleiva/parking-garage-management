package com.tonyleiva.parkinggarage.presentation.revenue;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record RevenueResponse(
    BigDecimal amount,
    String currency,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        timezone = "UTC")
    Instant timestamp) {}
