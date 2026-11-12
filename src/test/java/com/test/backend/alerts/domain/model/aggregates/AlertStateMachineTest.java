package com.test.backend.alerts.domain.model.aggregates;

import com.test.backend.alerts.domain.model.commands.CreateAlertCommand;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertStateMachineTest {

    private Alert alert;

    @BeforeEach
    void setUp() {
        var laboratory = new Laboratory();
        laboratory.setName("Bio-Safety Lab 02");
        var command = new CreateAlertCommand(
                "High Pressure Alert",
                "Pressure reading spike",
                "CRITICAL",
                "OPEN",
                "Bio-Safety Lab 02",
                "Just now",
                1L
        );
        alert = new Alert(command, laboratory);
    }

    @Test
    @DisplayName("Initial status matches created command status")
    void initialStatusMatchesCommand() {
        assertThat(alert.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Transitions from OPEN to ACKNOWLEDGED")
    void transitionsFromOpenToAcknowledged() {
        alert.acknowledge();
        assertThat(alert.getStatus()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    @DisplayName("Transitions from ACKNOWLEDGED to IN_PROGRESS")
    void transitionsFromAcknowledgedToInProgress() {
        alert.acknowledge();
        alert.attend();
        assertThat(alert.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Transitions from IN_PROGRESS to RESOLVED")
    void transitionsFromInProgressToResolved() {
        alert.acknowledge();
        alert.attend();
        alert.resolve();
        assertThat(alert.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("Transitions from RESOLVED to CLOSED")
    void transitionsFromResolvedToClosed() {
        alert.resolve();
        alert.close();
        assertThat(alert.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Throws exception when attempting to close an un-resolved alert")
    void throwsExceptionWhenClosingUnresolvedAlert() {
        assertThatThrownBy(() -> alert.close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot close an un-resolved alert");
    }

    @Test
    @DisplayName("Throws exception when attempting to acknowledge a resolved or closed alert")
    void throwsExceptionWhenAcknowledgingResolvedAlert() {
        alert.resolve();
        assertThatThrownBy(() -> alert.acknowledge())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot acknowledge an already resolved or closed alert");
    }
}
