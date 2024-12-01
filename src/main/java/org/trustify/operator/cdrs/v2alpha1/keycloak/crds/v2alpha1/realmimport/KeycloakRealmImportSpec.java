package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

import java.util.Map;
import org.keycloak.representations.idm.RealmRepresentation;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeycloakRealmImportSpec {
    @JsonPropertyDescription("The name of the Keycloak CR to reference, in the same namespace.")
    private String keycloakCRName;

    @JsonPropertyDescription("The RealmRepresentation to import into Keycloak.")
    private RealmRepresentation realm;

    @JsonProperty("resources")
    @JsonPropertyDescription("Compute Resources required by Keycloak container. If not specified, the value is inherited from the Keycloak CR.")
    private ResourceRequirements resourceRequirements;

    @JsonPropertyDescription("Optionally set to replace ENV variable placeholders in the realm import.")
    private Map<String, Placeholder> placeholders;

    public String getKeycloakCRName() {
        return keycloakCRName;
    }

    public void setKeycloakCRName(String keycloakCRName) {
        this.keycloakCRName = keycloakCRName;
    }

    public RealmRepresentation getRealm() {
        return realm;
    }

    public void setRealm(RealmRepresentation realm) {
        this.realm = realm;
    }

    public ResourceRequirements getResourceRequirements() {
        return resourceRequirements;
    }

    public void setResourceRequirements(ResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
    }

    public Map<String, Placeholder> getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(Map<String, Placeholder> placeholders) {
        this.placeholders = placeholders;
    }
}
