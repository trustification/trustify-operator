package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport;

import java.util.ArrayList;
import java.util.List;

public class KeycloakRealmImportStatusBuilder {
    private final KeycloakRealmImportStatusCondition readyCondition;
    private final KeycloakRealmImportStatusCondition startedCondition;
    private final KeycloakRealmImportStatusCondition hasErrorsCondition;

    private final List<String> notReadyMessages = new ArrayList<>();
    private final List<String> startedMessages = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    public KeycloakRealmImportStatusBuilder() {
        readyCondition = new KeycloakRealmImportStatusCondition();
        readyCondition.setType(KeycloakRealmImportStatusCondition.DONE);
        readyCondition.setStatus(false);

        startedCondition = new KeycloakRealmImportStatusCondition();
        startedCondition.setType(KeycloakRealmImportStatusCondition.STARTED);
        startedCondition.setStatus(false);

        hasErrorsCondition = new KeycloakRealmImportStatusCondition();
        hasErrorsCondition.setType(KeycloakRealmImportStatusCondition.HAS_ERRORS);
        hasErrorsCondition.setStatus(false);
    }

    public KeycloakRealmImportStatusBuilder addStartedMessage(String message) {
        startedCondition.setStatus(true);
        readyCondition.setStatus(false);
        hasErrorsCondition.setStatus(false);
        startedMessages.add(message);
        return this;
    }

    public KeycloakRealmImportStatusBuilder addDone() {
        startedCondition.setStatus(false);
        readyCondition.setStatus(true);
        hasErrorsCondition.setStatus(false);
        return this;
    }

    public KeycloakRealmImportStatusBuilder addNotReadyMessage(String message) {
        startedCondition.setStatus(false);
        readyCondition.setStatus(false);
        hasErrorsCondition.setStatus(false);
        notReadyMessages.add(message);
        return this;
    }

    public KeycloakRealmImportStatusBuilder addErrorMessage(String message) {
        startedCondition.setStatus(false);
        readyCondition.setStatus(false);
        hasErrorsCondition.setStatus(true);
        errorMessages.add(message);
        return this;
    }

    public KeycloakRealmImportStatus build() {
        readyCondition.setMessage(String.join("\n", notReadyMessages));
        startedCondition.setMessage(String.join("\n", startedMessages));
        hasErrorsCondition.setMessage(String.join("\n", errorMessages));

        KeycloakRealmImportStatus status = new KeycloakRealmImportStatus();
        status.setConditions(List.of(readyCondition, startedCondition, hasErrorsCondition));
        return status;
    }
}
