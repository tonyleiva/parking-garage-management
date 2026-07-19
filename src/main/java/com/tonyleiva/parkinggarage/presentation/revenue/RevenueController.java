package com.tonyleiva.parkinggarage.presentation.revenue;

import com.tonyleiva.parkinggarage.application.revenue.RevenueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RevenueController {
  private final RevenueService revenueService;

  public RevenueController(RevenueService revenueService) {
    this.revenueService = revenueService;
  }

  @GetMapping("/revenue")
  public ResponseEntity<RevenueResponse> getRevenue(@RequestBody RevenueRequest request) {
    var result = revenueService.calculate(request.date(), request.sector());
    return ResponseEntity.ok(
        new RevenueResponse(result.amount(), result.currency(), result.timestamp()));
  }
}
