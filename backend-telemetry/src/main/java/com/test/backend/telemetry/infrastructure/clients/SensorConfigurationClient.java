package com.test.backend.telemetry.infrastructure.clients;

import com.test.backend.shared.domain.model.events.SensorConfigurationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@FeignClient(name = "backend-automation", url = "${automation.service.url:http://backend-automation:8086}")
public interface SensorConfigurationClient {

    @GetMapping("/api/v1/automation/sensor-configurations/unit/{unit}")
    Optional<SensorConfigurationDTO> findByUnit(@PathVariable String unit);
}
