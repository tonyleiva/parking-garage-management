package com.tonyleiva.parkinggarage.domain.pricing;

import java.math.BigDecimal;

public record DynamicPrice(BigDecimal multiplier, BigDecimal hourlyPrice) {}
