package com.test.backend.shared.infrastructure.persistence;

import java.util.Map;

/**
 * Qualifies physical table names with their bounded-context catalog (Iteration 0, ADD point 4).
 *
 * <p>Native SQL does not go through Hibernate's metadata, so unqualified table references
 * would resolve against the connection's default catalog instead of the owning
 * {@code safelab_*} database. Every native query must therefore qualify its tables
 * through this registry.</p>
 *
 * <p>Keys are <b>physical</b> table names (after the snake_case + pluralize naming strategy),
 * i.e. exactly the names that exist in MySQL.</p>
 */
public final class BoundedContextCatalogs {

    private static final Map<String, String> TABLE_CATALOGS = Map.ofEntries(
            // safelab_iam
            Map.entry("users", "safelab_iam"),
            Map.entry("iam_roles", "safelab_iam"),
            Map.entry("user_iam_roles", "safelab_iam"),
            // safelab_labs
            Map.entry("workspaces", "safelab_labs"),
            Map.entry("laboratories", "safelab_labs"),
            Map.entry("storage_units", "safelab_labs"),
            Map.entry("lab_metrics", "safelab_labs"),
            Map.entry("lab_metric_subscriptions", "safelab_labs"),
            Map.entry("lab_alerts", "safelab_labs"),
            Map.entry("lab_activities", "safelab_labs"),
            Map.entry("lab_schedules", "safelab_labs"),
            Map.entry("mitigation_systems", "safelab_labs"),
            // safelab_telemetry
            Map.entry("sensor_readings", "safelab_telemetry"),
            Map.entry("metric_types", "safelab_telemetry"),
            Map.entry("telemetry_ingestion_idempotencies", "safelab_telemetry"),
            // safelab_alerts
            Map.entry("incidents", "safelab_alerts"),
            Map.entry("alerts", "safelab_alerts"),
            Map.entry("alert_metrics", "safelab_alerts"),
            Map.entry("corrective_actions", "safelab_alerts"),
            // safelab_automation
            Map.entry("user_profiles", "safelab_automation"),
            Map.entry("roles", "safelab_automation"),
            Map.entry("role_permissions", "safelab_automation"),
            Map.entry("user_workspace_accesses", "safelab_automation"),
            Map.entry("lab_user_accesses", "safelab_automation"),
            Map.entry("notification_preferences", "safelab_automation"),
            Map.entry("calibration_records", "safelab_automation"),
            Map.entry("general_settings", "safelab_automation"),
            Map.entry("equipment_thresholds", "safelab_automation"),
            Map.entry("automation_rules", "safelab_automation"),
            Map.entry("pending_invitations", "safelab_automation"),
            Map.entry("sensor_configurations", "safelab_automation"),
            // safelab_compliance
            Map.entry("audit_trails", "safelab_compliance"),
            Map.entry("compliance_reports", "safelab_compliance"),
            Map.entry("report_audit_links", "safelab_compliance"),
            // safelab_history
            Map.entry("history_records", "safelab_history"),
            // safelab_shared
            Map.entry("event_outboxes", "safelab_shared")
    );

    private BoundedContextCatalogs() {
    }

    /**
     * Returns {@code <catalog>.<table>} for the given physical table name.
     *
     * @throws IllegalStateException if the table is not registered in any bounded context
     */
    public static String qualify(String physicalTableName) {
        String catalog = TABLE_CATALOGS.get(physicalTableName);
        if (catalog == null) {
            throw new IllegalStateException(
                    "Table '" + physicalTableName + "' is not registered in BoundedContextCatalogs; "
                            + "add it with its safelab_* catalog (see @Table(catalog=...) of its entity)");
        }
        return catalog + "." + physicalTableName;
    }
}
