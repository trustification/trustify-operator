package org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpManagementSpec {

    @JsonPropertyDescription("Port of the management interface.")
    private Integer port = 9000;

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
