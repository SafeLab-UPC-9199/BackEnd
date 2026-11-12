package com.test.backend.automation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notification Preferences", description = "Notification Preferences management API")
@RestController
@RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferenceController {

    @GetMapping
    @Operation(summary = "Get all notification preferences")
    public ResponseEntity<List<String>> getAllNotificationPreferences() {
        return ResponseEntity.ok(List.of());
    }
}
