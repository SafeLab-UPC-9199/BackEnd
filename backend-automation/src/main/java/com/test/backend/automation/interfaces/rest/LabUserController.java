package com.test.backend.automation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Lab Users", description = "Lab Users management API")
@RestController
@RequestMapping("/api/v1/lab-users")
public class LabUserController {

    @GetMapping
    @Operation(summary = "Get all lab users")
    public ResponseEntity<List<String>> getAllLabUsers() {
        return ResponseEntity.ok(List.of());
    }
}
