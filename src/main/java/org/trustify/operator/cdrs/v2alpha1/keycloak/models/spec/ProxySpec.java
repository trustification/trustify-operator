package org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxySpec {
    @JsonPropertyDescription("The proxy headers that should be accepted by the server. Misconfiguration might leave the server exposed to security vulnerabilities.")
    private String headers;

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }
}
