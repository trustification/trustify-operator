package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpSpec {
    @JsonPropertyDescription("A secret containing the TLS configuration for HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
    private String tlsSecret;

    @JsonPropertyDescription("Enables the HTTP listener.")
    private Boolean httpEnabled;

    @JsonPropertyDescription("The used HTTP port.")
    private Integer httpPort = 8080;

    @JsonPropertyDescription("The used HTTPS port.")
    private Integer httpsPort = 8443;

    public String getTlsSecret() {
        return tlsSecret;
    }

    public void setTlsSecret(String tlsSecret) {
        this.tlsSecret = tlsSecret;
    }

    public Boolean getHttpEnabled() {
        return httpEnabled;
    }

    public void setHttpEnabled(Boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    public Integer getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(Integer httpsPort) {
        this.httpsPort = httpsPort;
    }
}
