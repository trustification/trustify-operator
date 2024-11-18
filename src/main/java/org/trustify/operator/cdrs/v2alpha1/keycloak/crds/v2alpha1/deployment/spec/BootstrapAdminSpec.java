package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BootstrapAdminSpec {

    public static class User {
        @JsonPropertyDescription("Name of the Secret that contains the username and password keys")
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Service {
        @JsonPropertyDescription("Name of the Secret that contains the client-id and client-secret keys")
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

    }

    //private Integer expiration;
    @JsonPropertyDescription("Configures the bootstrap admin user")
    private User user;
    @JsonPropertyDescription("Configures the bootstrap admin service account")
    private Service service;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

}