package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"enabled", "disabled"})
public class FeatureSpec implements Serializable {

    @JsonProperty("enabled")
    @JsonPropertyDescription("Enabled Keycloak features")
    private List<String> enabledFeatures;

    @JsonProperty("disabled")
    @JsonPropertyDescription("Disabled Keycloak features")
    private List<String> disabledFeatures;

    public List<String> getEnabledFeatures() {
        return enabledFeatures;
    }

    public void setEnabledFeatures(List<String> enabledFeatures) {
        this.enabledFeatures = enabledFeatures;
    }

    public List<String> getDisabledFeatures() {
        return disabledFeatures;
    }

    public void setDisabledFeatures(List<String> disabledFeatures) {
        this.disabledFeatures = disabledFeatures;
    }
}