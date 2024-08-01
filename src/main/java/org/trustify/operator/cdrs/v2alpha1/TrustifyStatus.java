package org.trustify.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TrustifyStatus {
    private List<TrustifyStatusCondition> conditions;

    public TrustifyStatus() {
        conditions = List.of(
                new TrustifyStatusCondition(TrustifyStatusCondition.AVAILABLE, false),
                new TrustifyStatusCondition(TrustifyStatusCondition.PROCESSING, false),
                new TrustifyStatusCondition(TrustifyStatusCondition.DEGRADED, false)
        );
    }

    public List<TrustifyStatusCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<TrustifyStatusCondition> conditions) {
        this.conditions = conditions;
    }

    @JsonIgnore
    public void setCondition(TrustifyStatusCondition condition) {
        List<TrustifyStatusCondition> conditions = this.conditions.stream()
                .filter(item -> !item.type().equals(condition.type()))
                .collect(Collectors.toList());
        conditions.add(condition);
        this.conditions = conditions;
    }

    @JsonIgnore
    public boolean isAvailable() {
        return this.conditions.stream()
                .anyMatch(item -> item.type().equals(TrustifyStatusCondition.AVAILABLE) && Objects.equals(item.status(), true));
    }

}
