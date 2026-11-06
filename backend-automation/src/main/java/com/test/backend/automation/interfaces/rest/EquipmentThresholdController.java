package com.test.backend.automation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Equipment Thresholds", description = "Equipment Thresholds management API")
@RestController
@RequestMapping("/api/v1/equipment-thresholds")
public class EquipmentThresholdController {

    @GetMapping
    @Operation(summary = "Get all equipment thresholds")
    public ResponseEntity<List<String>> getAllThresholds() {
        return ResponseEntity.ok(List.of());
    }
}
