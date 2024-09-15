package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.*;
import io.quarkus.logging.Log;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.db.DBService;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.cdrs.v2alpha1.server.ServerStoragePersistentVolumeClaim;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TrustifyDistConfigurator {

    private final Trustify cr;

    private final List<EnvVar> allEnvVars;
    private final List<Volume> allVolumes;
    private final List<VolumeMount> allVolumeMounts;

    public TrustifyDistConfigurator(Trustify cr) {
        this.cr = cr;
        this.allEnvVars = new ArrayList<>();
        this.allVolumes = new ArrayList<>();
        this.allVolumeMounts = new ArrayList<>();

        configureHttp();
        configureDatabase();
        configureStorage();
        configureOidc();
    }

    public List<EnvVar> getAllEnvVars() {
        return allEnvVars;
    }

    public List<Volume> getAllVolumes() {
        return allVolumes;
    }

    public List<VolumeMount> getAllVolumeMounts() {
        return allVolumeMounts;
    }

    private void configureHttp() {
        var optionMapper = optionMapper(cr.getSpec().httpSpec());
        configureTLS(optionMapper);

        List<EnvVar> envVars = optionMapper.getEnvVars();
        allEnvVars.addAll(envVars);

        // Force to use HTTP v4
        allEnvVars.add(new EnvVarBuilder()
                .withName("HTTP_SERVER_BIND_ADDR")
                .withValue("0.0.0.0")
                .build()
        );
    }

    private void configureTLS(OptionMapper<TrustifySpec.HttpSpec> optionMapper) {
        final String certFileOptionName = "HTTP_SERVER_TLS_CERTIFICATE_FILE";
        final String keyFileOptionName = "HTTP_SERVER_TLS_KEY_FILE";

        if (!ServerService.isTlsConfigured(cr)) {
            // for mapping and triggering warning in status if someone uses the fields directly
            optionMapper.mapOption(certFileOptionName);
            optionMapper.mapOption(keyFileOptionName);
            return;
        }

        optionMapper.mapOption("HTTP_SERVER_TLS_ENABLED", httpSpec -> true);
        optionMapper.mapOption(certFileOptionName, Constants.CERTIFICATES_FOLDER + "/tls.crt");
        optionMapper.mapOption(keyFileOptionName, Constants.CERTIFICATES_FOLDER + "/tls.key");

        var volume = new VolumeBuilder()
                .withName("trustify-tls-certificates")
                .withNewSecret()
                .withSecretName(cr.getSpec().httpSpec().tlsSecret())
                .withOptional(false)
                .endSecret()
                .build();

        var volumeMount = new VolumeMountBuilder()
                .withName(volume.getName())
                .withMountPath(Constants.CERTIFICATES_FOLDER)
                .build();

        allVolumes.add(volume);
        allVolumeMounts.add(volumeMount);
    }

    private void configureDatabase() {
        List<EnvVar> envVars = Optional.ofNullable(cr.getSpec().databaseSpec())
                .flatMap(databaseSpec -> {
                    if (databaseSpec.externalDatabase()) {
                        List<EnvVar> envs = optionMapper(cr.getSpec())
                                .mapOption("TRUSTD_DB_USER", spec -> databaseSpec.usernameSecret())
                                .mapOption("TRUSTD_DB_PASSWORD", spec -> databaseSpec.passwordSecret())
                                .mapOption("TRUSTD_DB_NAME", spec -> databaseSpec.name())
                                .mapOption("TRUSTD_DB_HOST", spec -> databaseSpec.host())
                                .mapOption("TRUSTD_DB_PORT", spec -> databaseSpec.port())
                                .getEnvVars();
                        return Optional.of(envs);
                    } else {
                        return Optional.empty();
                    }
                })
                .orElseGet(() -> optionMapper(cr.getSpec())
                        .mapOption("TRUSTD_DB_USER", spec -> DBDeployment.getUsernameSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_PASSWORD", spec -> DBDeployment.getPasswordSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_NAME", spec -> DBDeployment.getDatabaseName(cr))
                        .mapOption("TRUSTD_DB_HOST", spec -> DBService.getServiceName(cr))
                        .mapOption("TRUSTD_DB_PORT", spec -> DBDeployment.getDatabasePort(cr))
                        .getEnvVars()
                );

        allEnvVars.addAll(envVars);
    }

    private void configureStorage() {
        List<EnvVar> envVars = optionMapper(cr.getSpec())
                .mapOption("TRUSTD_STORAGE_FS_PATH", spec -> "/opt/trustify/storage")
                .getEnvVars();

        var volume = new VolumeBuilder()
                .withName("trustify-pvol")
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(ServerStoragePersistentVolumeClaim.getPersistentVolumeClaimName(cr))
                        .build()
                )
                .build();

        var volumeMount = new VolumeMountBuilder()
                .withName(volume.getName())
                .withMountPath("/opt/trustify")
                .build();

        allVolumes.add(volume);
        allVolumeMounts.add(volumeMount);

        allEnvVars.addAll(envVars);
    }

    private void configureOidc() {
        List<EnvVar> envVars = optionMapper(cr.getSpec())
                .mapOption("AUTH_DISABLED", spec -> true)
                .getEnvVars();
        allEnvVars.addAll(envVars);
    }

    private <T> OptionMapper<T> optionMapper(T optionSpec) {
        return new OptionMapper<>(optionSpec);
    }

    private static class OptionMapper<T> {
        private final T categorySpec;
        private final List<EnvVar> envVars;

        public OptionMapper(T optionSpec) {
            this.categorySpec = optionSpec;
            this.envVars = new ArrayList<>();
        }

        public List<EnvVar> getEnvVars() {
            return envVars;
        }

        public <R> OptionMapper<T> mapOption(String optionName, Function<T, R> optionValueSupplier) {
            if (categorySpec == null) {
                Log.debugf("No category spec provided for %s", optionName);
                return this;
            }

            R value = optionValueSupplier.apply(categorySpec);

            if (value == null || value.toString().trim().isEmpty()) {
                Log.debugf("No value provided for %s", optionName);
                return this;
            }

            EnvVarBuilder envVarBuilder = new EnvVarBuilder()
                    .withName(optionName);

            if (value instanceof SecretKeySelector) {
                envVarBuilder.withValueFrom(new EnvVarSourceBuilder().withSecretKeyRef((SecretKeySelector) value).build());
            } else {
                envVarBuilder.withValue(String.valueOf(value));
            }

            envVars.add(envVarBuilder.build());

            return this;
        }

        public <R> OptionMapper<T> mapOption(String optionName) {
            return mapOption(optionName, s -> null);
        }

        public <R> OptionMapper<T> mapOption(String optionName, R optionValue) {
            return mapOption(optionName, s -> optionValue);
        }

        protected <R extends Collection<?>> OptionMapper<T> mapOptionFromCollection(String optionName, Function<T, R> optionValueSupplier) {
            return mapOption(optionName, s -> {
                var value = optionValueSupplier.apply(s);
                if (value == null) return null;
                return value.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
            });
        }
    }

}
