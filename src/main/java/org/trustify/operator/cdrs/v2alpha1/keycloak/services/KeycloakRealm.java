package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImportSpec;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class KeycloakRealm {

    @Inject
    KubernetesClient k8sClient;

    public static String getKeycloakRealmImportName(Trustify cr) {
        return cr.getMetadata().getName() + "-realm-import";
    }

    public static String getRealmName(Trustify cr) {
        return "trustify";
    }

    public static String getFrontendClientName(Trustify cr) {
        return "frontend";
    }

    public static String getBackendClientName(Trustify cr) {
        return "backend";
    }

    public Optional<KeycloakRealmImport> getCurrentInstance(Trustify cr) {
        KeycloakRealmImport realmImport = k8sClient.resources(KeycloakRealmImport.class)
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(getKeycloakRealmImportName(cr))
                .get();
        return Optional.ofNullable(realmImport);
    }

    public KeycloakRealmImport initInstance(Trustify cr) {
        KeycloakRealmImport realmImport = new KeycloakRealmImport();

        realmImport.setMetadata(new ObjectMeta());
        realmImport.getMetadata().setName(getKeycloakRealmImportName(cr));
        realmImport.setSpec(new KeycloakRealmImportSpec());

        KeycloakRealmImportSpec spec = realmImport.getSpec();
        spec.setKeycloakCRName(KeycloakServer.getKeycloakName(cr));

        // Realm
        spec.setRealm(new RealmRepresentation());
        RealmRepresentation realmRepresentation = spec.getRealm();
        realmRepresentation.setRealm(getRealmName(cr));

        ClientRepresentation frontendClient = new ClientRepresentation();
        ClientRepresentation backendClient = new ClientRepresentation();
        realmRepresentation.setClients(List.of(frontendClient, backendClient));

        // Frontend Client
        frontendClient.setName(getFrontendClientName(cr));

        // Backend Client
        backendClient.setName(getBackendClientName(cr));

        // Default User
        UserRepresentation defaultUser = new UserRepresentation();
        realmRepresentation.setUsers(List.of(defaultUser));

        defaultUser.setUsername("admin");

        return k8sClient.resource(realmImport)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloakRealmImport -> {
            k8sClient.resource(keycloakRealmImport).delete();
        });
    }
}
