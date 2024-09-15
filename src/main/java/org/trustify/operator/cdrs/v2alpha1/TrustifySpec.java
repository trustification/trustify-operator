package org.trustify.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.SecretKeySelector;

import java.util.List;

public record TrustifySpec(
        @JsonPropertyDescription("Custom Trustify Server image to be used. For internal use only")
        String serverImage,

        @JsonPropertyDescription("Custom Trustify DB Server image to be used. For internal use only")
        String dbImage,

        @JsonPropertyDescription("Custom Image Pull Policy for images managed by the Operator")
        String imagePullPolicy,

        @JsonPropertyDescription("Secret(s) that might be used when pulling an image from a private container image registry or repository.")
        List<LocalObjectReference> imagePullSecrets,

        @JsonProperty("db")
        @JsonPropertyDescription("In this section you can find all properties related to connect to a database.")
        DatabaseSpec databaseSpec,

        @JsonProperty("hostname")
        @JsonPropertyDescription("In this section you can configure hostname and related properties.")
        HostnameSpec hostnameSpec,

        @JsonProperty("http")
        @JsonPropertyDescription("In this section you can configure features related to HTTP and HTTPS")
        HttpSpec httpSpec,

        @JsonProperty("serverResourceLimits")
        @JsonPropertyDescription("In this section you can configure resource limits settings for the Server.")
        ResourcesLimitSpec serverResourceLimitSpec
) {

    public TrustifySpec() {
        this(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public record DatabaseSpec(
            @JsonPropertyDescription("Use external database.")
            boolean externalDatabase,

            @JsonPropertyDescription("Size of the PVC to create. Valid only if externalDatabase=false")
            String pvcSize,

            @JsonPropertyDescription("In this section you can configure resource limits settings. Valid only if externalDatabase=false")
            ResourcesLimitSpec resourceLimits,

            @JsonPropertyDescription("The reference to a secret holding the username of the database user.")
            SecretKeySelector usernameSecret,

            @JsonPropertyDescription("The reference to a secret holding the password of the database user.")
            SecretKeySelector passwordSecret,

            @JsonPropertyDescription("The host of the database.")
            String host,

            @JsonPropertyDescription("The port of the database.")
            String port,

            @JsonPropertyDescription("The database name.")
            String name
    ) {
    }

    public record HostnameSpec(
            @JsonPropertyDescription("Hostname for the server.")
            String hostname
    ) {
    }

    public record HttpSpec(
            @JsonPropertyDescription("A secret containing the TLS configuration for HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
            String tlsSecret
    ) {
    }

    public record ResourcesLimitSpec(
            @JsonPropertyDescription("Requested CPU.")
            String cpuRequest,

            @JsonPropertyDescription("Limit CPU.")
            String cpuLimit,

            @JsonPropertyDescription("Requested memory.")
            String memoryRequest,

            @JsonPropertyDescription("Limit Memory.")
            String memoryLimit
    ) {
    }

}
