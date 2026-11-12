package com.test.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application against a throwaway MySQL container (Iteration 0, ADD point 4).
 *
 * <p>Tagged {@code it} (integration): excluded from the default {@code mvn clean test} run.
 * Run with: {@code mvn test -Dtest.excluded.groups=} (requires Docker).</p>
 */
@Tag("it")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "mqtt.enabled=false",
        // test-only HMAC secret (in production it comes from the AUTHORIZATION_JWT_SECRET env var)
        "authorization.jwt.secret=integration-test-secret-0123456789abcdef0123456789abcdef"
})
class BackendApplicationTests {

    private static final Set<String> EXPECTED_SCHEMAS = Set.of(
            "safelab_shared", "safelab_iam", "safelab_labs", "safelab_telemetry",
            "safelab_alerts", "safelab_automation", "safelab_compliance", "safelab_history");

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("safelab")
            // root is required: creating the 8 bounded-context schemas needs privileges
            // beyond the default 'test' user (which only owns its own database).
            .withUsername("root");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    DataSource dataSource;

    private List<String> queryStrings(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
            return rows;
        }
    }

    @Test
    @DisplayName("application context loads with MySQL")
    void contextLoads() {
    }

    @Test
    @DisplayName("the 8 bounded-context schemas exist")
    void eightBoundedContextSchemasExist() throws Exception {
        List<String> schemas = queryStrings(
                "SELECT schema_name FROM information_schema.schemata " +
                        "WHERE schema_name LIKE 'safelab\\_%' ORDER BY schema_name");
        assertThat(schemas).containsExactlyInAnyOrderElementsOf(EXPECTED_SCHEMAS);
    }

    @Test
    @DisplayName("Hibernate created tables inside every bounded-context schema")
    void tablesExistInEverySchema() throws Exception {
        for (String schema : EXPECTED_SCHEMAS) {
            List<String> tables = queryStrings(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schema + "'");
            assertThat(tables)
                    .as("tables in schema %s", schema)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("join tables live in their owner schemas (user_iam_roles, report_audit_links)")
    void joinTablesAreSchemaQualified() throws Exception {
        List<String> joinTables = queryStrings(
                "SELECT CONCAT(table_schema, '.', table_name) FROM information_schema.tables " +
                        "WHERE table_name IN ('user_iam_roles', 'report_audit_links')");
        assertThat(joinTables).containsExactlyInAnyOrder(
                "safelab_iam.user_iam_roles",
                "safelab_compliance.report_audit_links");
    }

    @Test
    @DisplayName("cross-schema join resolves: safelab_alerts.incidents x safelab_labs.laboratories")
    void crossSchemaJoinWorks() throws Exception {
        List<String> rows = queryStrings(
                "SELECT i.id FROM safelab_alerts.incidents i " +
                        "JOIN safelab_labs.laboratories l ON i.laboratory_id = l.id LIMIT 1");
        // 0 rows is fine (empty DB); what matters is that the join resolves across schemas.
        assertThat(rows).isNotNull();
    }

    @Test
    @DisplayName("cross-schema foreign keys exist (alerts -> labs)")
    void crossSchemaForeignKeysExist() throws Exception {
        List<String> fks = queryStrings(
                "SELECT CONCAT(kcu.table_schema, '.', kcu.table_name, ' -> ', " +
                        "kcu.referenced_table_schema, '.', kcu.referenced_table_name) " +
                        "FROM information_schema.key_column_usage kcu " +
                        "WHERE kcu.referenced_table_schema LIKE 'safelab\\_%' " +
                        "AND kcu.table_schema <> kcu.referenced_table_schema");
        assertThat(fks)
                .as("at least one FK crossing bounded-context schemas")
                .anySatisfy(fk -> assertThat(fk).startsWith("safelab_alerts.incidents -> safelab_labs.laboratories"));
    }
}
