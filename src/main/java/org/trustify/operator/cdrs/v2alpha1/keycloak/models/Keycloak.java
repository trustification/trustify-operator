package org.trustify.operator.cdrs.v2alpha1.keycloak.models;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(
    description="Represents a Keycloak Instance",
    displayName="Keycloak"
)
@Group("k8s.keycloak.org")
@Version("v2alpha1")
@ShortNames("kc")
@Plural("keycloaks")
public class Keycloak extends CustomResource<KeycloakSpec, KeycloakStatus> implements Namespaced {

}
