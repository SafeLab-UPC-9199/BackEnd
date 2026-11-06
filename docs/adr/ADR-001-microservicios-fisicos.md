# ADR-001: Arquitectura de microservicios físicos por bounded context

**Estado:** Aceptado  
**Fecha:** 2026-11-05  
**Decisores:** Equipo de desarrollo SafeLab

## Contexto

El informe ADD (sección 4.3 Iteraciones) plantea refactorizar el backend monolítico de SafeLab en una arquitectura de microservicios alineada con sus bounded contexts, desplegada como aplicación cloud native. Inicialmente se implementó un monolito modular (un solo JAR, una sola base de datos), lo cual cumplía con la descomposición lógica pero no con la descomposición física requerida para despliegue independiente por servicio.

## Decisión

Se adopta una arquitectura de microservicios físicos con las siguientes características:

1. **Mono-repo con módulos Maven separados:** Un módulo por bounded context (telemetry, alerts, labs, iam, compliance, automation, history, shared) dentro de un único repositorio Git. Esto equilibra aislamiento de servicios con simplicidad de coordinación para un equipo académico de 4 integrantes.

2. **API Gateway:** Spring Cloud Gateway como punto único de entrada, con rutas estáticas configuradas por servicio. El frontend consume una sola URL base a través del gateway.

3. **Base de datos por servicio:** Un esquema MySQL independiente por bounded context (safelab_telemetry, safelab_alerts, safelab_labs, safelab_iam, safelab_compliance, safelab_automation, safelab_history, safelab_shared) en una misma instancia MySQL.

4. **Comunicación:** OpenFeign para llamadas síncronas entre servicios; eventos de dominio (Spring Application Events) para desacoplamiento asíncrono.

5. **Despliegue:** Un contenedor Docker por servicio, orquestado con Docker Compose.

## Consecuencias

**Positivas:**
- Despliegue e independencia de escalado por servicio
- Fallas aisladas: un servicio caído no afecta a los demás
- Cada equipo puede trabajar en su bounded context sin conflictos de merge
- Tecnología heterogénea posible por servicio en el futuro

**Negativas:**
- Complejidad operativa mayor (orquestación, redes, service discovery)
- Comunicación entre servicios introduce latencia de red
- Gestión de transacciones distribuidas más compleja (mitigado con patrón Outbox)
- Sobrecarga de mantener múltiples Dockerfiles y configuraciones

## Alternativas consideradas

- **Multi-repo (un repositorio por servicio):** Rechazado por sobrecarga de coordinación para un proyecto académico
- **Monolito modular sin separación física:** Rechazado porque no cumple estrictamente el requerimiento de microservicios del informe ADD
- **Service Mesh (Istio/Linkerd):** Rechazado por complejidad excesiva para el alcance del proyecto
