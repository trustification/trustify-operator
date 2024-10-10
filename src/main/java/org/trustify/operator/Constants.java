package org.trustify.operator;

import java.util.Map;

public class Constants {
    public static final String CRDS_GROUP = "org.trustify";
    public static final String CRDS_VERSION = "v1alpha1";

    public static final String CONTEXT_LABELS_KEY = "labels";

    //
    public static final String TRUSTI_NAME = "trustify";
    public static final String TRUSTI_SERVER_NAME = "trustify-server";
    public static final String TRUSTI_DB_NAME = "trustify-db";

    public static final String OIDC_DB_NAME = "oidc-db";

    //
    public static final Map<String, String> DB_SELECTOR_LABELS = Map.of(
            "trustify-operator/group", "db"
    );
    public static final Map<String, String> SERVER_SELECTOR_LABELS = Map.of(
            "trustify-operator/group", "server"
    );

    public static final Map<String, String> OIDC_DB_SELECTOR_LABELS = Map.of(
            "trustify-operator/group", "oidc"
    );

    //
    public static final Integer HTTP_PORT = 8080;
    public static final Integer HTTPS_PORT = 8443;
    public static final Integer HTTP_INFRAESTRUCTURE_PORT = 9010;
    public static final String SERVICE_PROTOCOL = "TCP";

    //
    public static final String DB_PVC_SUFFIX = "-" + TRUSTI_DB_NAME + "-pvc";
    public static final String DB_SECRET_SUFFIX = "-" + TRUSTI_DB_NAME + "-secret";
    public static final String DB_DEPLOYMENT_SUFFIX = "-" + TRUSTI_DB_NAME + "-deployment";
    public static final String DB_SERVICE_SUFFIX = "-" + TRUSTI_DB_NAME + "-service";

    public static final String OIDC_DB_PVC_SUFFIX = "-" + OIDC_DB_NAME + "-pvc";
    public static final String OIDC_DB_SECRET_SUFFIX = "-" + OIDC_DB_NAME + "-secret";
    public static final String OIDC_DB_DEPLOYMENT_SUFFIX = "-" + OIDC_DB_NAME + "-deployment";
    public static final String OIDC_DB_SERVICE_SUFFIX = "-" + OIDC_DB_NAME + "-service";

    public static final String SERVER_DEPLOYMENT_SUFFIX = "-" + TRUSTI_SERVER_NAME + "-deployment";
    public static final String SERVER_SERVICE_SUFFIX = "-" + TRUSTI_SERVER_NAME + "-service";


    public static final String INGRESS_SUFFIX = "-" + TRUSTI_SERVER_NAME + "-ingress";

    //
    public static final String DB_SECRET_USERNAME = "username";
    public static final String DB_SECRET_PASSWORD = "password";
    public static final String DB_NAME = "database";
    public static final Integer DB_PORT= 5432;

    public static final String POSTGRESQL_PVC_SIZE = "10G";

    public static final String CERTIFICATES_FOLDER = "/mnt/certificates";
    public static final String WORKSPACES_FOLDER = "/mnt/workspace";
}
