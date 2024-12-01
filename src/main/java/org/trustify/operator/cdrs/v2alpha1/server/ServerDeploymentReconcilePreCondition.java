package org.trustify.operator.cdrs.v2alpha1.server;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.db.utils.DBUtils;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakRealmService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakServerService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;

import java.util.Optional;

@ApplicationScoped
public class ServerDeploymentReconcilePreCondition implements Condition<Deployment, Trustify> {

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> resource, Trustify cr, Context<Trustify> context) {
        boolean isDBRequired = ServerUtils.isServerDBRequired(cr);
        if (isDBRequired) {
            boolean isDBReady = DBUtils.isDeploymentRead(resource, cr, context);
            if (!isDBReady) {
                return false;
            }
        }

        boolean isKcRequired = KeycloakUtils.isKeycloakRequired(cr);
        if (isKcRequired) {
            Optional<KeycloakServerService> keycloakServerService = context.managedDependentResourceContext().get(Constants.CONTEXT_KEYCLOAK_SERVER_SERVICE_KEY, KeycloakServerService.class);
            Optional<KeycloakRealmService> keycloakRealmService = context.managedDependentResourceContext().get(Constants.CONTEXT_KEYCLOAK_REALM_SERVICE_KEY, KeycloakRealmService.class);
            if (keycloakServerService.isEmpty() || keycloakRealmService.isEmpty()) {
                throw new IllegalStateException("Could not find " + KeycloakServerService.class + " or " + KeycloakRealmService.class + " in the Context");
            }

            Optional<Keycloak> keycloakServer = keycloakServerService.get().getCurrentInstance(cr);
            if (keycloakServer.isEmpty()) {
                return false;
            }

            Optional<KeycloakRealmImport> realmImport = keycloakRealmService.get().getCurrentInstance(cr);
            if (realmImport.isEmpty()) {
                return false;
            }

            return KeycloakUtils.isKeycloakServerReady(keycloakServer.get()) && KeycloakUtils.isKeycloakRealmImportReady(realmImport.get());
        }

        return true;
    }

}
