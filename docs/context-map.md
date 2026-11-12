# Context Map - Bounded Contexts de SafeLab

## Contexto General

SafeLab es un sistema de monitoreo y gestión de laboratorios con sensores IoT. El dominio se descompone en los siguientes bounded contexts:

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   TELEMETRY     │      │     ALERTS      │      │      LABS       │
│  (Ingesta IoT)  │      │ (Alertas/Inc.)  │      │ (Activos/Labs)  │
└────────┬────────┘      └────────┬────────┘      └────────┬────────┘
         │                        │                        │
         │ publica                │ consume                │ provee
         │ eventos                │ eventos                │ umbrales
         ▼                        ▼                        ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│    IAM          │   │  COMPLIANCE   │   │  AUTOMATION   │
│ (Auth/Usuarios) │   │ (Auditoría)   │   │ (Reglas/UMbrales)│
└─────────────────┘      └─────────────────┘      └─────────────────┘
                              ▲
                              │ consume
                              │ eventos
┌─────────────────┐           │
│    HISTORY      │◄───────────┘
│ (Registros Hist.)│
└─────────────────┘
```

## Relaciones entre Contextos

| Contexto Origen | Contexto Destino | Tipo de Relación | Patrón | Mecanismo |
|-----------------|------------------|------------------|--------|-----------|
| Telemetry | Alerts | Publicador → Suscriptor | Customer-Supplier | Evento `TemperatureReadingRecorded` |
| Telemetry | History | Publicador → Suscriptor | Customer-Supplier | Evento `TemperatureReadingRecorded` |
| Alerts | Compliance | Publicador → Suscriptor | Customer-Supplier | Eventos `INCIDENT_*` |
| Labs | Telemetry | Proveedor → Consumidor | Shared Kernel | Umbrales de métrica por laboratorio |
| IAM | Todos | Soporte transversal | Shared Kernel | Autenticación, autorización, usuarios |
| Automation | Labs | Consumidor → Proveedor | Customer-Supplier | Configuraciones de sensores, reglas |

## Upstream/Downstream

- **Upstream:** Labs (provee umbrales y configuraciones), IAM (provee identidad)
- **Downstream:** Alerts (consume eventos de telemetría), Compliance (consume eventos de incidentes), History (consume eventos de telemetría e incidentes)

## Shared Kernel

- `shared-kernel`: DTOs comunes, eventos de dominio (`TemperatureReadingRecorded`, `IncidentCreated`, etc.), excepciones base, entidades de auditoría (`AuditableModel`, `SharedEventOutbox`).
