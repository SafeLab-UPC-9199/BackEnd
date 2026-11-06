package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.telemetry.interfaces.rest.resources.DashboardStatsResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Telemetry Stats", description = "Telemetry Dashboard Stats API")
@RestController
@RequestMapping("/api/v1/telemetry/stats")
public class TelemetryStatsController {

    @GetMapping
    @Operation(summary = "Get telemetry dashboard stats")
    public ResponseEntity<List<DashboardStatsResource>> getStats() {
        // Simplified stats for microservice boundary - no cross-context queries
        var stats = new DashboardStatsResource(
                1L,
                0,  // totalLaboratories - queried via labs service in future
                0,  // activeAlerts - queried via alerts service in future
                100, // systemsHealth
                0   // upcomingMaintenance
        );
        return ResponseEntity.ok(List.of(stats));
    }
}
