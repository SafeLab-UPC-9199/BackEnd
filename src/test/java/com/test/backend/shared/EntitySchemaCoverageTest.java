package com.test.backend.shared;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bounded-context schema split (Iteration 0, ADD point 4):
 * every {@code @Entity} must declare an explicit {@code @Table(catalog = "safelab_<module>")}
 * matching the module it lives in, so no table ever falls back to the default schema.
 *
 * <p>Note: on MySQL, JDBC {@code catalog} == database == what MySQL documentation calls
 * "schema". Hibernate's MySQL dialect reports {@code NameQualifierSupport.CATALOG}, so the
 * {@code catalog} attribute is the one that actually qualifies the generated DDL
 * ({@code @Table(schema=...)} would be silently dropped).</p>
 */
class EntitySchemaCoverageTest {

    private static final String BASE_PACKAGE = "com.test.backend";
    private static final String EXPECTED_PREFIX = "safelab_";

    private static final List<String> KNOWN_MODULES = List.of(
            "shared", "iam", "labs", "telemetry", "alerts", "automation", "compliance", "history");

    private List<Class<?>> scanEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(BASE_PACKAGE);
        List<Class<?>> entities = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            entities.add(Class.forName(candidate.getBeanClassName()));
        }
        return entities;
    }

    @Test
    @DisplayName("every @Entity declares @Table(catalog = \"safelab_<module>\") matching its package module")
    void everyEntityDeclaresItsBoundedContextCatalog() throws ClassNotFoundException {
        List<Class<?>> entities = scanEntities();

        // Sanity check that the classpath scan itself did not silently break.
        assertThat(entities).as("entity scan must find the known entity classes")
                .hasSizeGreaterThanOrEqualTo(30);

        for (Class<?> entity : entities) {
            Table table = entity.getAnnotation(Table.class);

            assertThat(table).as("@Table missing on %s", entity.getName())
                    .isNotNull();
            assertThat(table.catalog())
                    .as("@Table.catalog missing/blank on %s", entity.getName())
                    .isNotBlank();
            assertThat(table.schema())
                    .as("@Table.schema must NOT be used on MySQL (it is ignored) on %s", entity.getName())
                    .isEmpty();
            assertThat(table.catalog())
                    .as("@Table.catalog must start with '%s' on %s", EXPECTED_PREFIX, entity.getName())
                    .startsWith(EXPECTED_PREFIX);

            String module = table.catalog().substring(EXPECTED_PREFIX.length());
            assertThat(KNOWN_MODULES)
                    .as("unknown catalog '%s' on %s", table.catalog(), entity.getName())
                    .contains(module);

            assertThat(entity.getPackageName())
                    .as("catalog '%s' does not match package of %s", table.catalog(), entity.getName())
                    .startsWith(BASE_PACKAGE + "." + module + ".");
        }
    }

    @Test
    @DisplayName("all 8 bounded contexts are represented by at least one entity")
    void allEightBoundedContextsAreCovered() throws ClassNotFoundException {
        Set<String> catalogs = scanEntities().stream()
                .map(c -> c.getAnnotation(Table.class))
                .filter(t -> t != null && !t.catalog().isBlank())
                .map(t -> t.catalog())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(catalogs).containsExactlyInAnyOrderElementsOf(
                KNOWN_MODULES.stream().map(m -> EXPECTED_PREFIX + m).toList());
    }
}
