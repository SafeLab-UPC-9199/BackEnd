-- =============================================================================
-- SafeLab — migrate existing single-schema data into bounded-context schemas
-- (Architectural Design Backlog 0, point 4: data ownership per service).
--
-- FOR EXISTING DATABASES ONLY. Fresh deployments skip this file: Hibernate
-- creates the tables directly inside each safelab_* schema.
--
-- Usage (run ONCE against the database that currently holds the tables):
--   mysql -u <user> -p <current_database> < src/main/resources/sql/002-migrate-data.sql
--
-- Notes:
--   * RENAME TABLE keeps data, indexes and foreign keys; MySQL updates the FK
--     references of the renamed tables automatically (same instance).
--   * Statements fail if the target table already exists — that means that
--     table was already migrated; delete its line and re-run the rest.
--   * Rollback: rename every table back to its original name (drop the
--     safelab_*. prefix) in reverse order.
-- =============================================================================

RENAME TABLE
    -- safelab_shared
    event_outbox                TO safelab_shared.event_outbox,
    -- safelab_iam
    users                       TO safelab_iam.users,
    iam_roles                   TO safelab_iam.iam_roles,
    user_iam_roles              TO safelab_iam.user_iam_roles,
    -- safelab_labs
    workspaces                  TO safelab_labs.workspaces,
    laboratories                TO safelab_labs.laboratories,
    storage_units               TO safelab_labs.storage_units,
    lab_metrics                 TO safelab_labs.lab_metrics,
    lab_metric_subscriptions    TO safelab_labs.lab_metric_subscriptions,
    lab_alerts                  TO safelab_labs.lab_alerts,
    lab_activities              TO safelab_labs.lab_activities,
    lab_schedules               TO safelab_labs.lab_schedules,
    mitigation_systems          TO safelab_labs.mitigation_systems,
    -- safelab_telemetry
    sensor_readings             TO safelab_telemetry.sensor_readings,
    metric_types                TO safelab_telemetry.metric_types,
    telemetry_ingestion_idempotency TO safelab_telemetry.telemetry_ingestion_idempotency,
    -- safelab_alerts
    incidents                   TO safelab_alerts.incidents,
    alerts                      TO safelab_alerts.alerts,
    alert_metrics               TO safelab_alerts.alert_metrics,
    corrective_actions          TO safelab_alerts.corrective_actions,
    -- safelab_automation
    user_profiles               TO safelab_automation.user_profiles,
    roles                       TO safelab_automation.roles,
    role_permissions            TO safelab_automation.role_permissions,
    user_workspace_accesses     TO safelab_automation.user_workspace_accesses,
    lab_user_accesses           TO safelab_automation.lab_user_accesses,
    notification_preferences    TO safelab_automation.notification_preferences,
    calibration_records         TO safelab_automation.calibration_records,
    general_settings            TO safelab_automation.general_settings,
    equipment_thresholds        TO safelab_automation.equipment_thresholds,
    automation_rules            TO safelab_automation.automation_rules,
    pending_invitations         TO safelab_automation.pending_invitations,
    sensor_configurations       TO safelab_automation.sensor_configurations,
    -- safelab_compliance
    audit_trails                TO safelab_compliance.audit_trails,
    compliance_reports          TO safelab_compliance.compliance_reports,
    report_audit_links          TO safelab_compliance.report_audit_links,
    -- safelab_history
    history_records             TO safelab_history.history_records;

-- Verification: expect 8 rows, one per safelab_* schema.
SELECT table_schema, COUNT(*) AS tables_count
FROM information_schema.tables
WHERE table_schema LIKE 'safelab\_%'
GROUP BY table_schema
ORDER BY table_schema;
