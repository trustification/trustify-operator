package org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Truststore {

    @JsonPropertyDescription("Not used. To be removed in later versions.")
    private String name;
    private TruststoreSource secret;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TruststoreSource getSecret() {
        return secret;
    }

    public void setSecret(TruststoreSource secret) {
        this.secret = secret;
    }

}
