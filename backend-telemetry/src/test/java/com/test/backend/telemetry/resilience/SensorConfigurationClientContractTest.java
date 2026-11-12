package com.test.backend.telemetry.resilience;

import com.test.backend.telemetry.infrastructure.clients.SensorConfigurationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract between the telemetry Feign client and the real endpoint exposed by
 * backend-automation's {@code SensorConfigurationController}. A path or URL
 * drift here silently breaks sensor validation in the ingestion pipeline
 * (it did before Fase 4: the client called a non-existent path).
 */
class SensorConfigurationClientContractTest {

    private static final String EXPECTED_PATH = "/api/v1/sensor-configurations/{unit}";

    private FeignClient feignClient() {
        FeignClient fc = SensorConfigurationClient.class.getAnnotation(FeignClient.class);
        assertNotNull(fc, "SensorConfigurationClient must declare @FeignClient");
        return fc;
    }

    @Test
    @DisplayName("La URL por defecto resuelve al servicio automation de docker-compose")
    void feignClient_UsesComposeAutomationHostname() {
        String url = feignClient().url();
        assertTrue(url.contains("automation.service.url"),
                "URL must be externalized via automation.service.url: " + url);
        assertTrue(url.contains("safelab-backend-automation:8086"),
                "default URL must be the compose service hostname: " + url);
    }

    @Test
    @DisplayName("dismiss404=true: 404 -> Optional.empty() sin excepcion ni falla de circuito")
    void feignClient_Dismisses404() {
        assertTrue(feignClient().dismiss404(),
                "without dismiss404 a 404 becomes FeignException.NotFound, which the "
                        + "circuit breaker would record as a dependency failure");
    }

    @Test
    @DisplayName("findByUnit mapea el path real del controller de automation")
    void findByUnit_MapsRealControllerPath() throws Exception {
        Method m = SensorConfigurationClient.class.getMethod("findByUnit", String.class);

        GetMapping mapping = m.getAnnotation(GetMapping.class);
        assertNotNull(mapping, "findByUnit must declare @GetMapping");
        assertArrayEquals(new String[]{EXPECTED_PATH}, mapping.value(),
                "backend-automation exposes @RequestMapping(\"/api/v1/sensor-configurations\")"
                        + " + @GetMapping(\"/{unit}\")");

        PathVariable pathVariable = m.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("unit", pathVariable.value());

        assertTrue(m.getGenericReturnType() instanceof ParameterizedType,
                "return type must be Optional<SensorConfigurationDTO>");
        assertEquals(Optional.class, ((ParameterizedType) m.getGenericReturnType()).getRawType());
    }
}
