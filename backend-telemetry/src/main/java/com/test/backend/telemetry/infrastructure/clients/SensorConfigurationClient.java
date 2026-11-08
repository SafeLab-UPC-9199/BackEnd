package com.test.backend.telemetry.infrastructure.clients;

import com.test.backend.shared.domain.model.events.SensorConfigurationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * Read-only Feign client to the automation service (sensor registry).
 *
 * <p>{@code dismiss404 = true}: an unregistered unit answers HTTP 404 with an
 * empty body. Without dismiss404 feign would throw {@code FeignException.NotFound},
 * which the circuit breaker would record as a dependency failure (and the retry
 * aspect would retry 3 times). With dismiss404 the response reaches feign's
 * {@code OptionalDecoder}, which maps 404/204 straight to {@code Optional.empty()}
 * without decoding the empty body — a legitimate "not found" is then handled by
 * the caller and never pollutes the circuit breaker state (ADR-004).
 */
@FeignClient(
        name = "backend-automation",
        url = "${automation.service.url:http://safelab-backend-automation:8086}",
        dismiss404 = true)
public interface SensorConfigurationClient {

    /**
     * Mirrors {@code SensorConfigurationController} in backend-automation:
     * {@code @RequestMapping("/api/v1/sensor-configurations")} + {@code @GetMapping("/{unit}")}.
     */
    @GetMapping("/api/v1/sensor-configurations/{unit}")
    Optional<SensorConfigurationDTO> findByUnit(@PathVariable("unit") String unit);
}
