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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public static String getUIClientName(Trustify cr) {
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

        RoleRepresentation chickenUserRole = new RoleRepresentation("chicken-user", "User of the application", false);
        RoleRepresentation chickenManagerRole = new RoleRepresentation("chicken-manager", "User of the application", false);
        RoleRepresentation chickenAdminRole = new RoleRepresentation("chicken-admin", "Admin of the application", false);
        realmRepresentation.getRoles().setRealm(List.of(
                chickenUserRole,
                chickenManagerRole,
                chickenAdminRole
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
        BiConsumer<ClientScopeRepresentation, List<RoleRepresentation>> applyRolesToScope = (scopeRepresentation, roles) -> {
            realmRepresentation
                    .clientScopeScopeMapping(scopeRepresentation.getName())
                    .setRoles(roles.stream()
                            .map(RoleRepresentation::getName)
                            .collect(Collectors.toSet())
                    );
        };

//        applyRolesToScope.accept(readDocumentScope, List.of(chickenManagerRole, chickenUserRole));
        applyRolesToScope.accept(createDocumentScope, List.of(chickenManagerRole));
//        applyRolesToScope.accept(updateDocumentScope, List.of(chickenManagerRole));
        applyRolesToScope.accept(deleteDocumentScope, List.of(chickenManagerRole));

        // Users
        UserRepresentation developerUser = new UserRepresentation();
        UserRepresentation adminUser = new UserRepresentation();
        realmRepresentation.setUsers(List.of(
                developerUser,
                adminUser
        ));

        // Developer User
        developerUser.setUsername("developer");
        developerUser.setEmail("developer@trustify.org");
        developerUser.setFirstName("Developer");
        developerUser.setLastName("Developer");
        developerUser.setEnabled(true);
        developerUser.setRealmRoles(List.of(
                "default-roles-trustify",
                "offline_access",
                "uma_authorization",
                chickenUserRole.getName())
        );

        CredentialRepresentation developerCredentials = new CredentialRepresentation();
        developerCredentials.setType(CredentialRepresentation.PASSWORD);
        developerCredentials.setValue("password");
        developerCredentials.setTemporary(false);

        developerUser.setCredentials(List.of(developerCredentials));

        // Admin User
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@trustify.org");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("Admin");
        adminUser.setEnabled(true);
        adminUser.setRealmRoles(List.of(
                "default-roles-trustify",
                "offline_access",
                "uma_authorization",
                chickenUserRole.getName(),
                chickenManagerRole.getName(),
                chickenAdminRole.getName()
        ));

        CredentialRepresentation adminCredentials = new CredentialRepresentation();
        adminCredentials.setType(CredentialRepresentation.PASSWORD);
        adminCredentials.setValue("password");
        adminCredentials.setTemporary(false);

        adminUser.setCredentials(List.of(adminCredentials));

        // Clients
        if (realmRepresentation.getClients() == null || realmRepresentation.getClients().isEmpty()) {
            realmRepresentation.setClients(new ArrayList<>());
        }

        ClientRepresentation uiClient = new ClientRepresentation();
        ClientRepresentation backendClient = new ClientRepresentation();

        realmRepresentation.getClients().addAll(List.of(
                uiClient,
                backendClient
        ));

        // UI Client
        uiClient.setClientId(getUIClientName(cr));
        uiClient.setRedirectUris(List.of("*"));
        uiClient.setWebOrigins(List.of("*"));
        uiClient.setPublicClient(true);

        uiClient.setDefaultClientScopes(List.of(
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
        backendClient.setRedirectUris(List.of("*"));
        backendClient.setWebOrigins(List.of("*"));

        backendClient.setStandardFlowEnabled(false);
        backendClient.setDirectAccessGrantsEnabled(false);
        backendClient.setServiceAccountsEnabled(true);
        backendClient.setFrontchannelLogout(false);
        backendClient.setFullScopeAllowed(true);

        backendClient.setDefaultClientScopes(List.of(
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

        return k8sClient.resource(realmImport)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    private RealmRepresentation getDefaultRealm() {
        try {
            InputStream defaultRealmInputStream = KeycloakRealm.class.getClassLoader().getResourceAsStream("realm.json");
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
