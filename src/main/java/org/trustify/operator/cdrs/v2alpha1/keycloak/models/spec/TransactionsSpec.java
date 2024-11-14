package org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionsSpec implements Serializable {

    @JsonPropertyDescription("Determine whether Keycloak should use a non-XA datasource in case the database does not support XA transactions.")
    private Boolean xaEnabled;

    public Boolean isXaEnabled() {
        return xaEnabled;
    }

    public void setXaEnabled(Boolean xaEnabled) {
        this.xaEnabled = xaEnabled;
    }
}
