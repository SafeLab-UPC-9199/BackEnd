package com.test.backend.labs.domain.model.entities;

import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab_metric_subscriptions", catalog = "safelab_labs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"laboratory_id", "metric_type_id"}))
public class LabMetricSubscription extends AuditableModel {

    // laboratory_id FK is now owned by Laboratory.metricSubscriptions
    // (@OneToMany @JoinColumn); keeping a @Column here would map the same
    // column twice ("Repeated column").

    @Column(name = "metric_type_id", nullable = false)
    private Long metricTypeId;

    @Column(name = "min_threshold")
    private Double minThreshold;

    @Column(name = "max_threshold")
    private Double maxThreshold;

    @Column(nullable = false)
    private boolean active = true;
}
