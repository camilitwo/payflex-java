package com.payflex.web;

import com.payflex.dto.DashboardStatsResponse;
import com.payflex.service.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/merchants")
@RequiredArgsConstructor
public class DashboardStatsController {

    private final DashboardStatsService dashboardStatsService;

    @GetMapping("/{merchantId}/dashboard/stats")
    public Mono<ResponseEntity<DashboardStatsResponse>> getDashboardStats(
            @PathVariable String merchantId) {

        log.info("Getting dashboard stats for merchant: {}", merchantId);

        return dashboardStatsService.getMerchantDashboardStats(merchantId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}

