package org.trustify.operator.cdrs.v2alpha1.keycloak.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.model.annotation.LabelSelector;
import io.fabric8.kubernetes.model.annotation.StatusReplicas;
import io.javaoperatorsdk.operator.api.ObservedGenerationAware;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KeycloakStatus implements ObservedGenerationAware {

    @LabelSelector
    private String selector;
    @StatusReplicas
    private Integer instances;
    private Long observedGeneration;

    private List<KeycloakStatusCondition> conditions;

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public Integer getInstances() {
        return instances;
    }

    public void setInstances(Integer instances) {
        this.instances = instances;
    }

    public List<KeycloakStatusCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<KeycloakStatusCondition> conditions) {
        this.conditions = conditions;
    }

    public Optional<KeycloakStatusCondition> findCondition(String type) {
        if (conditions == null || conditions.isEmpty()) {
            return Optional.empty();
        }
        return conditions.stream().filter(c -> type.equals(c.getType())).findFirst();
    }

    @JsonIgnore
    public boolean isReady() {
        return findCondition(KeycloakStatusCondition.READY).map(KeycloakStatusCondition::getStatus).orElse(false);
    }

    @Override
    public Long getObservedGeneration() {
        return observedGeneration;
    }

    @Override
    public void setObservedGeneration(Long generation) {
        this.observedGeneration = generation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeycloakStatus status = (KeycloakStatus) o;
        return Objects.equals(getConditions(), status.getConditions())
                && Objects.equals(getInstances(), status.getInstances())
                && Objects.equals(getSelector(), status.getSelector())
                && Objects.equals(getObservedGeneration(), status.getObservedGeneration());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getConditions(), getInstances(), getSelector(), getObservedGeneration());
    }
}
