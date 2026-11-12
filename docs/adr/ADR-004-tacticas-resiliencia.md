# ADR-004: Tácticas de resiliencia (Iteración 2)

**Estado:** Aceptado  
**Fecha:** 2026-11-05  
**Decisores:** Equipo de desarrollo SafeLab

## Contexto

El informe ADD (Iteración 2) requiere cumplir con atributos de calidad de latencia, disponibilidad y confiabilidad para el caso de uso de ingesta de telemetría IoT y generación de alertas. En una arquitectura de microservicios, las fallas en cadena (cascading failures) son un riesgo crítico cuando un servicio depende de otro.

## Decisión

Se aplican las siguientes tácticas de resiliencia usando Resilience4j:

1. **Circuit Breaker:** En llamadas Feign entre servicios (ej. `telemetry` llama a `labs` para validar sensores). Configuración: slidingWindowSize=10, failureRateThreshold=50%, waitDurationInOpenState=30s, transición automática a half-open.

2. **Retry:** Máximo 3 reintentos con espera de 1s entre intentos para llamadas transitorias.

3. **Rate Limiting:** Filtro de servlet con Bucket4j que limita a 60 peticiones por minuto por API key/IP en el endpoint de ingesta HTTP.

4. **Fallback methods:** Métodos de alternativa cuando el circuit breaker está abierto, que permiten continuar operando con funcionalidad reducida.

## Consecuencias

**Positivas:**
- El sistema no colapsa cuando un servicio dependiente falla
- Recuperación automática tras fallos transitorios
- Protección contra ataques de denegación de servicio por rate limiting
- Métricas de salud del circuit breaker expuestas vía Actuator

**Negativas:**
- Complejidad adicional en el código de llamadas entre servicios
- Posible respuesta degradada (fallback) en lugar de error claro al cliente

## Alternativas consideradas

- **Hystrix:** Rechazado por estar en modo de mantenimiento
- **Polly (Java port):** Rechazado por menor integración nativa con Spring Boot
- **Sin resiliencia:** Rechazado por no cumplir los atributos de calidad de la Iteración 2
