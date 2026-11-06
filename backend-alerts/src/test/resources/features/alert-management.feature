@alerts @incident
Funcionalidad: Gestión de alertas e incidentes (US08 / US24 - Alertas térmicas)

  Como operador de laboratorio
  Quiero recibir alertas cuando se detecten excursiones térmicas
  Para tomar acciones correctivas oportunas

  Escenario: Crear una alerta de temperatura crítica
    Dado que existe una lectura de temperatura fuera de rango en el laboratorio "Lab-04"
    Cuando se procesa la lectura por el sistema de alertas
    Entonces se crea una alerta con severidad "CRITICAL"
    Y el estado inicial es "OPEN"
    Y se genera un evento Outbox "INCIDENT_CREATED"

  Escenario: Crear un incidente a partir de una alerta
    Dado que existe una alerta activa para el laboratorio "Lab-04"
    Cuando se solicita crear un incidente con severidad "CRITICAL"
    Entonces se crea un incidente vinculado a la alerta
    Y el estado del incidente es "OPEN"
    Y se genera un evento Outbox "INCIDENT_ACKNOWLEDGED"

  Escenario: Escalar incidente cuando no se resuelve a tiempo
    Dado que existe un incidente en estado "OPEN" con escalación configurada
    Cuando pasa el tiempo máximo de escalación sin resolución
    Entonces el incidente pasa a estado "ESCALATED"
    Y se asigna al siguiente rol de escalación
    Y se genera un evento Outbox "INCIDENT_ESCALATED"

  Escenario: Resolver incidente con acción correctiva
    Dado que existe un incidente en estado "ACKNOWLEDGED"
    Cuando un técnico registra la acción correctiva tomada
    Entonces el incidente pasa a estado "RESOLVED"
    Y se registra la acción correctiva en el historial
