package org.trustify.operator.cdrs.v2alpha1.ui;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Config;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakRealm;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakServer;
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.utils.CRDUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@KubernetesDependent(labelSelector = UIDeployment.LABEL_SELECTOR, resourceDiscriminator = UIDeploymentDiscriminator.class)
@ApplicationScoped
public class UIDeployment extends CRUDKubernetesDependentResource<Deployment, Trustify>
        implements Matcher<Deployment, Trustify>, Condition<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui";

    @Inject
    Config config;

    public UIDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Trustify cr, Context<Trustify> context) {
        return newDeployment(cr, context);
    }

    @Override
    public Result<Deployment> match(Deployment actual, Trustify cr, Context<Trustify> context) {
        final var container = actual.getSpec()
                .getTemplate().getSpec().getContainers()
                .stream()
                .findFirst();

        return Result.nonComputed(container
                .map(c -> c.getImage() != null)
                .orElse(false)
        );
    }

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Deployment.class, new UIDeploymentDiscriminator())
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private Deployment newDeployment(Trustify cr, Context<Trustify> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getDeploymentName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(contextLabels)
                .addToLabels("component", "ui")
                .addToLabels(Map.of(
                        "app.openshift.io/runtime", "nodejs"
                ))
                .withAnnotations(Map.of("app.openshift.io/connects-to", """
                        [{"apiVersion": "apps/v1", "kind":"Deployment", "name": "%s"}]
                        """.formatted(ServerDeployment.getDeploymentName(cr))
                ))
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = Constants.UI_SELECTOR_LABELS;
        String image = Optional.ofNullable(cr.getSpec().uiImage()).orElse(config.uiImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(config.imagePullPolicy());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), TrustifySpec::uiResourceLimitSpec)
                .orElse(null);

        return new DeploymentSpecBuilder()
                .withStrategy(new DeploymentStrategyBuilder()
                        .withType("Recreate")
                        .build()
                )
                .withReplicas(1)
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(selectorLabels)
                        .build()
                )
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata()
                        .withLabels(Stream
                                .concat(contextLabels.entrySet().stream(), selectorLabels.entrySet().stream())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        )
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(60L)
                                .withImagePullSecrets(cr.getSpec().imagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.TRUSTI_UI_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(getEnvVars(cr))
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(Constants.HTTP_PORT)
                                                        .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand(
                                                                "/bin/sh",
                                                                "-c",
                                                                "ps -A | grep node"
                                                        )
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(10)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(5)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/")
                                                        .withNewPort(Constants.HTTP_PORT)
                                                        .withScheme("HTTP")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(10)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(5)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withResources(new ResourceRequirementsBuilder()
                                                .withRequests(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuRequest).orElse("100m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryRequest).orElse("350Mi"))
                                                ))
                                                .withLimits(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuLimit).orElse("500m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryLimit).orElse("800Mi"))
                                                ))
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();
    }

    private List<EnvVar> getEnvVars(Trustify cr) {
        List<EnvVar> oidcEnvVars = Optional.ofNullable(cr.getSpec().oidcSpec())
                .map(oidcSpec -> {
                    if (oidcSpec.enabled()) {
                        TrustifySpec.OidcProviderType providerType = Objects.nonNull(oidcSpec.type()) ? oidcSpec.type() : TrustifySpec.OidcProviderType.EMBEDDED;

                        List<EnvVar> providerEnvs;
                        switch (providerType) {
                            case EXTERNAL -> {
                                providerEnvs = Optional.ofNullable(oidcSpec.externalOidcSpec())
                                        .map(externalOidcSpec -> List.of(
                                                new EnvVarBuilder()
                                                        .withName("OIDC_CLIENT_ID")
                                                        .withValue(externalOidcSpec.uiClientId())
                                                        .build(),
                                                new EnvVarBuilder()
                                                        .withName("OIDC_SERVER_URL")
                                                        .withValue(externalOidcSpec.serverUrl())
                                                        .build()
                                        ))
                                        .orElseGet(ArrayList::new);
                            }
                            case EMBEDDED -> {
                                providerEnvs = List.of(
                                        new EnvVarBuilder()
                                                .withName("OIDC_CLIENT_ID")
                                                .withValue(KeycloakRealm.getUIClientName(cr))
                                                .build(),
                                        new EnvVarBuilder()
                                                .withName("OIDC_SERVER_URL")
                                                .withValue(KeycloakServer.getServiceHost(cr))
                                                .build(),
                                        new EnvVarBuilder()
                                                .withName("OIDC_SERVER_IS_EMBEDDED")
                                                .withValue(Boolean.TRUE.toString())
                                                .build(),
                                        new EnvVarBuilder()
                                                .withName("OIDC_SERVER_EMBEDDED_PATH")
                                                .withValue(String.format("/auth/realms/%s", KeycloakRealm.getRealmName(cr)))
                                                .build()
                                );
                            }
                            default -> providerEnvs = Collections.emptyList();
                        }

                        List<EnvVar> result = new ArrayList<>();
                        result.add(new EnvVarBuilder()
                                .withName("AUTH_REQUIRED")
                                .withValue(Boolean.TRUE.toString())
                                .build()
                        );
                        result.addAll(providerEnvs);
                        return result;
                    } else {
                        return List.of(new EnvVarBuilder()
                                .withName("AUTH_REQUIRED")
                                .withValue(Boolean.FALSE.toString())
                                .build()
                        );
                    }
                })
                .orElseGet(() -> List.of(new EnvVarBuilder()
                        .withName("AUTH_REQUIRED")
                        .withValue(Boolean.FALSE.toString())
                        .build()
                ));

        List<EnvVar> envVars = Arrays.asList(
                new EnvVarBuilder()
                        .withName("ANALYTICS_ENABLED")
                        .withValue("false")
                        .build(),
                new EnvVarBuilder()
                        .withName("TRUSTIFY_API_URL")
                        .withValue(ServerService.getServiceUrl(cr))
                        .build(),
                new EnvVarBuilder()
                        .withName("UI_INGRESS_PROXY_BODY_SIZE")
                        .withValue("50m")
                        .build(),
                new EnvVarBuilder()
                        .withName("NODE_EXTRA_CA_CERTS")
                        .withValue("/opt/app-root/src/ca.crt")
                        .build()
        );

        List<EnvVar> result = new ArrayList<>();
        result.addAll(oidcEnvVars);
        result.addAll(envVars);

        return result;
    }

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.UI_DEPLOYMENT_SUFFIX;
    }
}
