package org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngressSpec {

    @JsonProperty("enabled")
    private boolean ingressEnabled = true;
    
    @JsonProperty("className")
    private String ingressClassName;

    @JsonProperty("annotations")
    @JsonPropertyDescription("Additional annotations to be appended to the Ingress object")
    Map<String, String> annotations;

    public boolean isIngressEnabled() {
        return ingressEnabled;
    }

    public void setIngressEnabled(boolean enabled) {
        this.ingressEnabled = enabled;
    }
    
    public String getIngressClassName() {
        return ingressClassName;
    }
    
    public void setIngressClassName(String className) {
        this.ingressClassName = className;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }
}
