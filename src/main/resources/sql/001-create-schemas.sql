-- =============================================================================
-- SafeLab — bounded-context schemas (Architectural Design Backlog 0, point 4)
-- One MySQL schema per bounded context. Idempotent: safe to run repeatedly.
--
-- Executed automatically at startup via spring.sql.init (see application.properties)
-- before Hibernate DDL, so hibernate can create tables inside each schema.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS safelab_shared;      -- outbox, cross-cutting events
CREATE SCHEMA IF NOT EXISTS safelab_iam;         -- identity & access management
CREATE SCHEMA IF NOT EXISTS safelab_labs;        -- lab asset & infrastructure management
CREATE SCHEMA IF NOT EXISTS safelab_telemetry;   -- telemetry & environment monitoring
CREATE SCHEMA IF NOT EXISTS safelab_alerts;      -- intelligent alerting & incidents
CREATE SCHEMA IF NOT EXISTS safelab_automation;  -- smart automation & scheduling
CREATE SCHEMA IF NOT EXISTS safelab_compliance;  -- operational compliance & audit logging
CREATE SCHEMA IF NOT EXISTS safelab_history;     -- history records
