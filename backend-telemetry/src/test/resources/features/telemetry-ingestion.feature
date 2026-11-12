# language: es
@telemetry @ingestion
Característica: Ingesta de telemetría de sensores (US05 - Monitoreo térmico)

  Como operador de laboratorio
  Quiero enviar lecturas de temperatura de sensores
  Para detectar excursiones térmicas sin puntos ciegos

  Escenario: Ingestión exitosa de lectura de temperatura por HTTP
    Dado que el dispositivo edge tiene una API Key válida configurada
    Y el sensor unit "Lab-04-TEMP-01" está registrado en el sistema
    Cuando envío una petición POST a "/api/v1/telemetry/ingestion" con headers:
      | X-Api-Key       | test-key-123  |
      | Idempotency-Key | idem-temp-001 |
    Y envío una petición POST a "/api/v1/telemetry/ingestion" con body:
      | unit      | Lab-04-TEMP-01       |
      | timestamp | 1733456789000        |
      | metrics   | {"temperature": 5.4} |
    Entonces la respuesta debe ser 202
    Y el body debe contener "ACCEPTED"

  Escenario: Rechazo por sensor no registrado
    Dado que el dispositivo edge tiene una API Key válida configurada
    Cuando envío una petición POST a "/api/v1/telemetry/ingestion" con headers:
      | X-Api-Key | test-key-123 |
    Y envío una petición POST a "/api/v1/telemetry/ingestion" con body:
      | unit    | UNKNOWN-SENSOR       |
      | metrics | {"temperature": 5.4} |
    Entonces la respuesta debe ser 404
    Y el body debe contener "Unregistered sensor unit"

  Escenario: Idempotencia - misma Idempotency-Key no duplica procesamiento
    Dado que el dispositivo edge tiene una API Key válida configurada
    Y el sensor unit "Lab-04-TEMP-01" está registrado en el sistema
    Cuando envío dos peticiones POST consecutivas con la misma Idempotency-Key "idem-dup-001"
    Entonces ambas respuestas deben ser 202
    Y solo se debe crear una lectura en la base de datos

  Escenario: Rechazo por API Key inválida
    Cuando envío una petición POST a "/api/v1/telemetry/ingestion" con headers:
      | X-Api-Key | invalid-key |
    Entonces la respuesta debe ser 401
