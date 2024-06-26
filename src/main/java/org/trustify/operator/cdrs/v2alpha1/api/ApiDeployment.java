package org.trustify.operator.cdrs.v2alpha1.api;

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
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.controllers.TrustifyDistConfigurator;
import org.trustify.operator.utils.CRDUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@KubernetesDependent(labelSelector = ApiDeployment.LABEL_SELECTOR, resourceDiscriminator = ApiDeploymentDiscriminator.class)
@ApplicationScoped
public class ApiDeployment extends CRUDKubernetesDependentResource<Deployment, Trustify>
        implements Matcher<Deployment, Trustify>, Condition<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=api";

    @Inject
    Config config;

    public ApiDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Trustify cr, Context<Trustify> context) {
        TrustifyDistConfigurator distConfigurator = new TrustifyDistConfigurator(cr);
        return newDeployment(cr, context, distConfigurator);
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
        return context.getSecondaryResource(Deployment.class, new ApiDeploymentDiscriminator())
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
    private Deployment newDeployment(Trustify cr, Context<Trustify> context, TrustifyDistConfigurator distConfigurator) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getDeploymentName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(contextLabels)
                .addToLabels("component", "api")
                .withAnnotations(Map.of("app.openshift.io/connects-to", """
                        [{"apiVersion": "apps/v1", "kind":"Deployment", "name": "%s"}]
                        """.formatted(DBDeployment.getDeploymentName(cr))
                ))
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context, distConfigurator))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context, TrustifyDistConfigurator distConfigurator) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = Constants.API_SELECTOR_LABELS;
        String image = config.apiImage();
        String imagePullPolicy = config.imagePullPolicy();

        List<EnvVar> envVars = distConfigurator.getAllEnvVars();
        List<Volume> volumes = distConfigurator.getAllVolumes();
        List<VolumeMount> volumeMounts = distConfigurator.getAllVolumeMounts();

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), TrustifySpec::apiResourceLimitSpec)
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
                                .withTerminationGracePeriodSeconds(70L)
                                .withImagePullSecrets(cr.getSpec().imagePullSecrets())
//                                .withServiceAccountName(Constants.TRUSTI_NAME)
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.TRUSTI_API_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(envVars)
                                        .withCommand("/usr/local/bin/trustd", "api", "--devmode")
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(8080)
                                                        .build()
//                                                new ContainerPortBuilder()
//                                                        .withName("https")
//                                                        .withProtocol("TCP")
//                                                        .withContainerPort(8443)
//                                                        .build()
                                        )
//                                        .withLivenessProbe(new ProbeBuilder()
//                                                .withHttpGet(new HTTPGetActionBuilder()
//                                                        .withPath("/q/health/live")
//                                                        .withNewPort(8080)
//                                                        .withScheme("HTTP")
//                                                        .build()
//                                                )
//                                                .withInitialDelaySeconds(5)
//                                                .withTimeoutSeconds(10)
//                                                .withPeriodSeconds(10)
//                                                .withSuccessThreshold(1)
//                                                .withFailureThreshold(3)
//                                                .build()
//                                        )
//                                        .withReadinessProbe(new ProbeBuilder()
//                                                .withHttpGet(new HTTPGetActionBuilder()
//                                                        .withPath("/q/health/ready")
//                                                        .withNewPort(8080)
//                                                        .withScheme("HTTP")
//                                                        .build()
//                                                )
//                                                .withInitialDelaySeconds(5)
//                                                .withTimeoutSeconds(1)
//                                                .withPeriodSeconds(10)
//                                                .withSuccessThreshold(1)
//                                                .withFailureThreshold(3)
//                                                .build()
//                                        )
//                                        .withStartupProbe(new ProbeBuilder()
//                                                .withHttpGet(new HTTPGetActionBuilder()
//                                                        .withPath("/q/health/started")
//                                                        .withNewPort(8080)
//                                                        .withScheme("HTTP")
//                                                        .build()
//                                                )
//                                                .withInitialDelaySeconds(5)
//                                                .withTimeoutSeconds(1)
//                                                .withPeriodSeconds(10)
//                                                .withSuccessThreshold(1)
//                                                .withFailureThreshold(3)
//                                                .build()
//                                        )
                                        .withVolumeMounts(volumeMounts)
                                        .withResources(new ResourceRequirementsBuilder()
                                                .withRequests(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuRequest).orElse("50m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryRequest).orElse("64Mi"))
                                                ))
                                                .withLimits(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuLimit).orElse("250m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryLimit).orElse("256Mi"))
                                                ))
                                                .build()
                                        )
                                        .build()
                                )
                                .withVolumes(volumes)
                                .build()
                        )
                        .build()
                )
                .build();
    }

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.API_DEPLOYMENT_SUFFIX;
    }
}
