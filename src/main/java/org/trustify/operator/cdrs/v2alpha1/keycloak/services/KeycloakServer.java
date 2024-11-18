package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.ValueOrSecret;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakHttpTlsSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.KeycloakSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.DatabaseSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.HostnameSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.HttpSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.IngressSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImportSpec;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class KeycloakServer {

    @Inject
    KubernetesClient k8sClient;

    public static String getKeycloakName(Trustify cr) {
        return cr.getMetadata().getName() + "-keycloak";
    }

    public Keycloak initInstance(Trustify cr) {
        Keycloak keycloak = new Keycloak();

        keycloak.setMetadata(new ObjectMeta());
        keycloak.getMetadata().setName(getKeycloakName(cr));
        keycloak.setSpec(new KeycloakSpec());

        KeycloakSpec spec = keycloak.getSpec();
        spec.setInstances(1);

        // Database
        spec.setDatabaseSpec(new DatabaseSpec());
        DatabaseSpec databaseSpec = spec.getDatabaseSpec();
        databaseSpec.setVendor("postgres");
        databaseSpec.setHost(KeycloakDBService.getServiceName(cr));
        databaseSpec.setPort(5432);
        databaseSpec.setDatabase(KeycloakDBDeployment.getDatabaseName(cr));

        databaseSpec.setUsernameSecret(KeycloakDBSecret.getUsernameKeySelector(cr));
        databaseSpec.setPasswordSecret(KeycloakDBSecret.getPasswordKeySelector(cr));

        // Https
        spec.setHttpSpec(new HttpSpec());
        HttpSpec httpSpec = spec.getHttpSpec();
        httpSpec.setTlsSecret(KeycloakHttpTlsSecret.getSecretName(cr));

        // Ingress
        spec.setIngressSpec(new IngressSpec());
        spec.getIngressSpec().setIngressEnabled(false);

        // Hostname
        spec.setHostnameSpec(new HostnameSpec());
        spec.getHostnameSpec().setStrict(false);

        // Additional options
        spec.setAdditionalOptions(List.of(
                new ValueOrSecret("http-relative-path", "/auth", null)
        ));

        return k8sClient.resource(keycloak)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    public Optional<Keycloak> getCurrentInstance(Trustify cr) {
        Keycloak keycloak = k8sClient.resources(Keycloak.class)
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(getKeycloakName(cr))
                .get();
        return Optional.ofNullable(keycloak);
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloak -> {
            k8sClient.resource(keycloak).delete();
        });
    }

    public static String getServiceHost(Trustify cr) {
        return String.format("%s://%s.%s.svc:%s", "https", cr.getMetadata().getName() + "-keycloak-service", cr.getMetadata().getNamespace(), 8443);
    }
}
