package com.test.backend.automation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "General Settings", description = "General Settings management API")
@RestController
@RequestMapping("/api/v1/general-settings")
public class GeneralSettingController {

    @GetMapping
    @Operation(summary = "Get all general settings")
    public ResponseEntity<List<String>> getAllSettings() {
        return ResponseEntity.ok(List.of());
    }
}
