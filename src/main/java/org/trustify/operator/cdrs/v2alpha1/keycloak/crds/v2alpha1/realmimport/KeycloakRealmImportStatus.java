package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

import static org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImportStatusCondition.DONE;

public class KeycloakRealmImportStatus {
    private List<KeycloakRealmImportStatusCondition> conditions;

    public List<KeycloakRealmImportStatusCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<KeycloakRealmImportStatusCondition> conditions) {
        this.conditions = conditions;
    }

    @JsonIgnore
    public boolean isDone() {
        return conditions
                .stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getStatus()) && c.getType().equals(DONE));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeycloakRealmImportStatus status = (KeycloakRealmImportStatus) o;
        return Objects.equals(getConditions(), status.getConditions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getConditions());
    }
}
