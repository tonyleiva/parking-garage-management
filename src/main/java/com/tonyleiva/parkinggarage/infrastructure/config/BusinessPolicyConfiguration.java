package com.tonyleiva.parkinggarage.infrastructure.config;

import com.tonyleiva.parkinggarage.domain.pricing.DynamicPricingPolicy;
import com.tonyleiva.parkinggarage.domain.pricing.ParkingFeePolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessPolicyConfiguration {
  @Bean Clock clock() { return Clock.systemUTC(); }
  @Bean DynamicPricingPolicy dynamicPricingPolicy() { return new DynamicPricingPolicy(); }
  @Bean ParkingFeePolicy parkingFeePolicy() { return new ParkingFeePolicy(); }
}
