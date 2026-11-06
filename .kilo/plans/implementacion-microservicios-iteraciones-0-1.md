# Plan: Implementación completa de Iteraciones 0 y 1 + Microservicios (Opción A)

## Objetivo
Transformar el backend monolito modular actual en microservicios físicos independientes por bounded context, con API Gateway, resiliencia, observabilidad, testing BDD y documentación ADD completa, cumpliendo las Iteraciones 0 y 1 del informe ADD.

## Alcance
- Backend: mono-repo con módulos Maven separados por bounded context + `gateway` + `shared-kernel`.
- Frontend: seguir consumiendo vía Gateway (una sola URL base).
- Configuración: `application.properties` por servicio + variables de entorno.
- Base de datos: un esquema MySQL por servicio en la misma instancia (`safelab_telemetry`, `safelab_alerts`, etc.).
- Comunicación: Gateway + OpenFeign síncrono para consultas; eventos de dominio asíncronos para desacoplamiento.
- Despliegue: un contenedor Docker por servicio + base de datos.

## Estructura de módulos (mono-repo)

```
BackEnd/
├── pom.xml (parent aggregator)
├── shared-kernel/            (DTOs comunes, exceptions, eventos de dominio)
├── gateway/                  (Spring Cloud Gateway, rutas estáticas por servicio)
├── backend-telemetry/        (puerto 8081)
├── backend-alerts/           (puerto 8082)
├── backend-labs/             (puerto 8083)
├── backend-iam/              (puerto 8084)
├── backend-compliance/       (puerto 8085)
├── backend-automation/       (puerto 8086)
├── backend-history/          (puerto 8087)
├── backend-shared/           (infraestructura transversal: Outbox, filtros, Actuator)
└── docker-compose.yml        (un contenedor por servicio + MySQL)
```

## Tareas detalladas

### Fase 1: Preparación y módulo shared-kernel
1. Crear `shared-kernel` con DTOs comunes, eventos de dominio (`TemperatureReadingRecorded`, `IncidentCreated`, etc.) y excepciones base.
2. Mover dependencias compartidas (Lombok, Jackson, validation) al parent POM.
3. Compilar `shared-kernel` como dependencia de los demás módulos.

### Fase 2: División en módulos de servicio
Para cada bounded context (`telemetry`, `alerts`, `labs`, `iam`, `compliance`, `automation`, `history`, `shared`):
4. Crear módulo Maven independiente (`backend-<contexto>`).
5. Mover código fuente (`application`, `domain`, `infrastructure`, `interfaces`) al nuevo módulo ajustando paquetes.
6. Añadir `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `mysql-connector-j`.
7. Configurar `spring.datasource.url` apuntando a esquema propio (`safelab_<contexto>`).
8. Añadir `server.port` distinto por servicio.
9. Crear `Dockerfile` por servicio.
10. Compilar cada módulo individualmente y verificar `mvn compile`.

### Fase 3: API Gateway
11. Crear módulo `gateway` con `spring-cloud-starter-gateway`.
12. Definir rutas estáticas en `application.properties` (o YAML) mapeando `/api/v1/telemetry/**` → `backend-telemetry:8081`, etc.
13. Configurar CORS en el gateway para permitir el frontend (`localhost:4200`, Vercel).
14. Añadir `spring-cloud-starter-openfeign` para llamadas síncronas entre servicios si es necesario.
15. Crear `Dockerfile` para el gateway.

### Fase 4: Resiliencia (Iteración 2)
16. Añadir `resilience4j-spring-boot3` a cada servicio.
17. Configurar Circuit Breaker en clientes Feign y en servicios de ingestión/alerta.
18. Configurar Retry y Timeout para llamadas HTTP entre servicios.
19. Configurar Bulkhead para proteger hilos de trabajo críticos.

### Fase 5: Observabilidad (Iteración 2)
20. Añadir `spring-boot-starter-actuator` a cada servicio.
21. Exponer `/actuator/health`, `/actuator/info`, `/actuator/metrics`.
22. Implementar propagación de `correlationId` en filtros de servlet y eventos de dominio.
23. Configurar logs estructurados con `correlationId`.
24. (Opcional) Añadir Micrometer + Prometheus si se dispone de infraestructura de monitoreo.

### Fase 6: Validación y rate limiting (Iteración 1/2)
25. Añadir validación de rangos físicos en `TelemetryIngestionService` (ej. temperatura entre -50 y 100 °C).
26. Mantener y mejorar `RateLimitingFilter` por dispositivo/API key en el servicio `telemetry`.
27. Validar `Idempotency-Key` en HTTP y MQTT en el servicio `telemetry`.

### Fase 7: Despliegue e infraestructura
28. Actualizar `docker-compose.yml` con un servicio por backend + `gateway` + `safelab-mysql`.
29. Crear `init_safelab.sql` que cree los 8 esquemas (`safelab_telemetry`, `safelab_alerts`, etc.).
30. Documentar variables de entorno requeridas por servicio.

### Fase 8: Testing Suite (BDD/Gherkin)
31. Añadir dependencias `cucumber-spring`, `cucumber-junit` (o equivalente) a cada servicio de prueba crítica (`telemetry`, `alerts`, `iam`).
32. Crear archivos `.feature` en `src/test/resources/features/` por cada User Story crítica:
    - `authentication.feature` (US04: registro/login de usuario)
    - `telemetry-ingestion.feature` (US05: envío de lecturas de sensores)
    - `alert-management.feature` (US08/US24: generación y consulta de alertas/incidentes)
    - `incident-management.feature` (US24: creación/escalada de incidentes)
33. Crear runners de Cucumber/JUnit para ejecutar los `.feature`.
34. Vincular cada escenario con su User Story en comentarios del archivo `.feature`.

### Fase 9: Documentación ADD
35. Crear `docs/adr/` con Architectural Decision Records para:
    - División en microservicios físicos (Iteración 1).
    - Uso de Outbox transversal (Iteración 0).
    - Eventos de dominio para desacoplamiento (Iteración 1).
    - Tácticas de resiliencia (Iteración 2).
36. Crear `docs/context-map.md` con las relaciones entre bounded contexts (Customer-Supplier, Shared Kernel, etc.).
37. Actualizar `docs/architecture.md` con diagramas C4 (Nivel 1 y 2) y vistas 4+1.

### Fase 10: Evidencias para el informe
38. Crear `docs/testing/TESTING_SUITE_EVIDENCE.md` con tabla:
    | Repository | Branch | Commit Id | Commit Message | Fecha |
39. Capturas de Swagger de cada servicio (endpoints expuestos por el Gateway).
40. Commits descriptivos y push a `develop` y `main`.

## Validación
- `mvn compile` en cada módulo.
- `mvn test` ejecutando pruebas JUnit existentes y nuevos tests Cucumber.
- Levantar `docker-compose up` y verificar que el Gateway routea correctamente a cada servicio.
- Abrir Swagger en el Gateway y confirmar que aparecen endpoints de todos los servicios.
- Probar flujo: `telemetry` ingest → evento `TemperatureReadingRecorded` → `alerts` evalúa → crea alerta/incidente.

## Riesgos
- Acoplamiento entre servicios vía OpenFeign puede crear fallas en cadena; mitigado con Circuit Breaker.
- Migración de esquemas de base de datos: se debe crear `init_safelab.sql` idempotente.
- Sincronización de versiones entre módulos en mono-repo; mitigado con parent POM y versiones centralizadas.

## Entregable final
Backend dividido en microservicios físicos con Gateway, resiliencia, observabilidad, testing BDD, documentación ADD y despliegue por contenedor, listo para el Sprint Review.