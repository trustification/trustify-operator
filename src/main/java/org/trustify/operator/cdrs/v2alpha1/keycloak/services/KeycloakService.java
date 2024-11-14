package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.KeycloakSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.HostnameSpec;

@ApplicationScoped
public class KeycloakService {

    @Inject
    KubernetesClient k8sClient;

    public void createKeycloak() {
        k8sClient
                .resource(create())
                .create();
    }

    public Keycloak create() {
        Keycloak keycloak = new Keycloak();
        keycloak.setMetadata(new ObjectMeta());
        keycloak.getMetadata().setName("example-keycloak");

        keycloak.setSpec(new KeycloakSpec());
        keycloak.getSpec().setInstances(1);
        keycloak.getSpec().setHostnameSpec(new HostnameSpec());
        keycloak.getSpec().getHostnameSpec().setHostname("example.org");

        return keycloak;
    }

}
