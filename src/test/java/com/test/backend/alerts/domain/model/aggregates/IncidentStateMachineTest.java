package com.test.backend.alerts.domain.model.aggregates;

import com.test.backend.alerts.domain.model.entities.CorrectiveAction;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateMachineTest {

    private Incident incident;

    @BeforeEach
    void setUp() {
        var laboratory = new Laboratory();
        laboratory.setName("Bio-Safety Lab 01");
        incident = new Incident(laboratory, "Thermal Spike", "Temperature exceeded threshold", "CRITICAL", 15, "COORDINATOR");
    }

    @Test
    @DisplayName("Initial state is OPEN")
    void initialStateIsOpen() {
        assertThat(incident.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Transitions from OPEN to ACKNOWLEDGED")
    void transitionsFromOpenToAcknowledged() {
        incident.acknowledge();
        assertThat(incident.getStatus()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    @DisplayName("Transitions from ACKNOWLEDGED to IN_PROGRESS")
    void transitionsFromAcknowledgedToInProgress() {
        incident.acknowledge();
        incident.attend();
        assertThat(incident.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Transitions from IN_PROGRESS to RESOLVED with CorrectiveAction")
    void transitionsFromInProgressToResolved() {
        incident.acknowledge();
        incident.attend();
        var correctiveAction = new CorrectiveAction(incident, 123L, "Calibrated refrigeration thermostat");
        incident.resolve(correctiveAction);

        assertThat(incident.getStatus()).isEqualTo("RESOLVED");
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getCorrectiveAction()).isNotNull();
    }

    @Test
    @DisplayName("Transitions from RESOLVED to CLOSED")
    void transitionsFromResolvedToClosed() {
        var correctiveAction = new CorrectiveAction(incident, 123L, "Calibrated refrigeration thermostat");
        incident.resolve(correctiveAction);
        incident.close();

        assertThat(incident.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Throws exception when attempting to close an un-resolved incident")
    void throwsExceptionWhenClosingUnresolvedIncident() {
        assertThatThrownBy(() -> incident.close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot close an un-resolved incident");
    }

    @Test
    @DisplayName("Throws exception when attempting to attend a resolved incident")
    void throwsExceptionWhenAttendingResolvedIncident() {
        var correctiveAction = new CorrectiveAction(incident, 123L, "Replaced cooling valve");
        incident.resolve(correctiveAction);

        assertThatThrownBy(() -> incident.attend())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot attend an already resolved or closed incident");
    }

    @Test
    @DisplayName("Transitions to ESCALATED and updates next supervisory role")
    void transitionsToEscalated() {
        incident.escalate("HEAD_OF_SAFETY");
        assertThat(incident.getStatus()).isEqualTo("ESCALATED");
        assertThat(incident.getEscalationNextRole()).isEqualTo("HEAD_OF_SAFETY");
    }
}
