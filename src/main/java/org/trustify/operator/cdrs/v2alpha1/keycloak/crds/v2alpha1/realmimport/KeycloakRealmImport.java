package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("k8s.keycloak.org")
@Version("v2alpha1")
public class KeycloakRealmImport extends CustomResource<KeycloakRealmImportSpec, KeycloakRealmImportStatus> implements Namespaced {

    @JsonIgnore
    public String getRealmName() {
        return this.getSpec().getRealm().getRealm();
    }

}
