package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.keycloak.representations.idm.*;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImportSpec;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@ApplicationScoped
public class KeycloakRealm {

    @Inject
    KubernetesClient k8sClient;

    @Inject
    ObjectMapper objectMapper;

    Function<String, ClientScopeRepresentation> generateClientScope = scope -> {
        ClientScopeRepresentation scopeRepresentation = new ClientScopeRepresentation();
        scopeRepresentation.setName(scope);
        scopeRepresentation.setProtocol("openid-connect");
        return scopeRepresentation;
    };

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

    public static String getRealmClientPath(Trustify cr) {
        return String.format("%s/realms/%s", KeycloakServer.RELATIVE_PATH, KeycloakRealm.getRealmName(cr));
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
        spec.setRealm(getDefaultRealm());
        RealmRepresentation realmRepresentation = spec.getRealm();
        realmRepresentation.setRealm(getRealmName(cr));

        // Realm roles
        if (realmRepresentation.getRoles() == null) {
            realmRepresentation.setRoles(new RolesRepresentation());
        }
        if (realmRepresentation.getRoles().getRealm() == null) {
            realmRepresentation.getRoles().setRealm(new ArrayList<>());
        }

        RoleRepresentation userRole = new RoleRepresentation("user", "User of the application", false);
        RoleRepresentation adminRole = new RoleRepresentation("admin", "Admin of the application", false);
        realmRepresentation.getRoles().setRealm(List.of(
                userRole,
                adminRole
        ));

        // Scopes
        if (realmRepresentation.getClientScopes() == null) {
            realmRepresentation.setClientScopes(new ArrayList<>());
        }

        ClientScopeRepresentation readDocumentScope = generateClientScope.apply("read:document");
        ClientScopeRepresentation createDocumentScope = generateClientScope.apply("create:document");
        ClientScopeRepresentation updateDocumentScope = generateClientScope.apply("update:document");
        ClientScopeRepresentation deleteDocumentScope = generateClientScope.apply("delete:document");

        realmRepresentation.getClientScopes().addAll(List.of(
                readDocumentScope,
                createDocumentScope,
                updateDocumentScope,
                deleteDocumentScope
        ));

        // Role-Scope Mapping
        Consumer<ClientScopeRepresentation> applyRealmScopeMapping = scopeRepresentation -> {
            realmRepresentation
                    .clientScopeScopeMapping(scopeRepresentation.getName())
                    .setRoles(Set.of(userRole.getName()));
        };

        applyRealmScopeMapping.accept(readDocumentScope);
        applyRealmScopeMapping.accept(createDocumentScope);
        applyRealmScopeMapping.accept(updateDocumentScope);
        applyRealmScopeMapping.accept(deleteDocumentScope);

        // Default User
        UserRepresentation developerUser = new UserRepresentation();
        realmRepresentation.setUsers(List.of(developerUser));

        developerUser.setUsername("developer");
        developerUser.setEmail("developer@trustify.org");
        developerUser.setFirstName("Developer");
        developerUser.setLastName("Developer");
        developerUser.setEnabled(true);
        developerUser.setRealmRoles(List.of(
                "default-roles-trustify",
                "offline_access",
                "uma_authorization",
                userRole.getName())
        );

        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setValue("password");
        credentialRepresentation.setTemporary(false);

        developerUser.setCredentials(List.of(credentialRepresentation));

        // Clients
        if (realmRepresentation.getClients() == null || realmRepresentation.getClients().isEmpty()) {
            realmRepresentation.setClients(new ArrayList<>());
        }

        ClientRepresentation frontendClient = new ClientRepresentation();
        ClientRepresentation backendClient = new ClientRepresentation();

        realmRepresentation.getClients().addAll(List.of(
                frontendClient,
                backendClient
        ));

        // Frontend Client
        frontendClient.setClientId(getFrontendClientName(cr));
        frontendClient.setRedirectUris(List.of("*"));
        frontendClient.setWebOrigins(List.of("*"));
        frontendClient.setPublicClient(true);

        frontendClient.setDefaultClientScopes(List.of(
                "acr",
                "address",
                "basic",
                "email",
                "microprofile-jwt",
                "offline_access",
                "phone",
                "profile",
                "roles",

                readDocumentScope.getName(),
                createDocumentScope.getName(),
                updateDocumentScope.getName(),
                deleteDocumentScope.getName()
        ));

        // Backend Client
        backendClient.setClientId(getBackendClientName(cr));
        backendClient.setBearerOnly(true);

        return k8sClient.resource(realmImport)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    private RealmRepresentation getDefaultRealm() {
        try {
            InputStream defaultRealmInputStream = KeycloakRealm.class.getClassLoader().getResourceAsStream("default-realm.json");
            ObjectReader objectReader = objectMapper.readerFor(RealmRepresentation.class);
            return objectReader.readValue(defaultRealmInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloakRealmImport -> {
            k8sClient.resource(keycloakRealmImport).delete();
        });
    }
}
