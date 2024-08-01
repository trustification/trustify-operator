package org.trustify.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TrustifyStatus {
    private List<TrustifyStatusCondition> conditions;

    public TrustifyStatus() {
        conditions = new ArrayList<>();
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
                .filter(item -> !item.getType().equals(condition.getType()))
                .collect(Collectors.toList());
        conditions.add(condition);
        this.conditions = conditions;
    }

    @JsonIgnore
    public boolean isAvailable() {
        return this.conditions.stream()
                .anyMatch(item -> item.getType().equals(TrustifyStatusCondition.SUCCESSFUL) && Objects.equals(item.getStatus(), true));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustifyStatus status = (TrustifyStatus) o;
        return Objects.equals(getConditions(), status.getConditions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getConditions());
    }
}
