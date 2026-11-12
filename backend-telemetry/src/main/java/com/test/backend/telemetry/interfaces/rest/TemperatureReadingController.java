package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.telemetry.domain.model.aggregates.SensorReading;
import com.test.backend.telemetry.domain.services.SensorReadingQueryService;
import com.test.backend.telemetry.interfaces.rest.resources.TemperatureReadingResource;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.SensorReadingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Telemetry Readings", description = "Generic Telemetry Readings API — works for any metric type")
@RestController
@RequestMapping("/api/v1/telemetry")
public class TemperatureReadingController {

    private final SensorReadingQueryService sensorReadingQueryService;
    private final SensorReadingRepository sensorReadingRepository;

    public TemperatureReadingController(SensorReadingQueryService sensorReadingQueryService,
                                         SensorReadingRepository sensorReadingRepository) {
        this.sensorReadingQueryService = sensorReadingQueryService;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    /**
     * Generic endpoint: returns chart-ready readings for any metric type.
     * Usage: GET /api/v1/telemetry/readings?metricKey=temperature
     */
    @GetMapping("/readings")
    @Operation(summary = "Get historical readings for a specific metric type")
    public ResponseEntity<List<TemperatureReadingResource>> getReadingsByMetricKey(
            @RequestParam String metricKey) {

        List<SensorReading> readings = sensorReadingRepository.findByMetricTypeKey(metricKey);

        Map<String, List<SensorReading>> grouped = readings.stream()
                .filter(r -> r.getDate() != null)
                .collect(Collectors.groupingBy(SensorReading::getDate));

        List<TemperatureReadingResource> resources = new ArrayList<>();
        long idCounter = 1L;

        List<String> sortedDates = new ArrayList<>(grouped.keySet());
        Collections.sort(sortedDates);

        for (String date : sortedDates) {
            List<SensorReading> dateReadings = grouped.get(date);
            Map<String, Double> values = new HashMap<>();
            for (var r : dateReadings) {
                values.put(r.getLaboratoryId().toString(), r.getValue());
            }
            resources.add(new TemperatureReadingResource(idCounter++, date, values));
        }

        return ResponseEntity.ok(resources);
    }

    /**
     * Legacy endpoint preserved for backward compatibility.
     */
    @GetMapping("/temperature-readings")
    @Operation(summary = "Get historical temperature readings (legacy endpoint)")
    public ResponseEntity<List<TemperatureReadingResource>> getAllTemperatureReadings() {
        return getReadingsByMetricKey("temperature");
    }
}
