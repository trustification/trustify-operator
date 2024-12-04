package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ListVisitFromServerGetDeleteRecreateWaitApplicable;
import io.javaoperatorsdk.operator.Operator;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.ingress.AppIngress;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.db.service.DBService;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.service.ServerService;
import org.trustify.operator.cdrs.v2alpha1.ui.deployment.UIDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.service.UIService;

import java.util.List;
import java.util.Objects;

public abstract class ReconcilerBaseTest {

    @ConfigProperty(name = "related.image.db")
    String dbImage;

    @ConfigProperty(name = "related.image.ui")
    String uiImage;

    @ConfigProperty(name = "related.image.server")
    String serverImage;

    @Inject
    KubernetesClient client;

    @Inject
    Operator operator;

    protected Namespace namespace = null;
    protected Job imagePullerJob = null;

    private Trustify trustify;
    private ListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> resources;

    @BeforeEach
    public void beforeEach() {
        // Create namespace
        namespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName(client.getNamespace())
                .endMetadata()
                .build();
        if (client.resource(namespace).get() == null) {
            client.resource(namespace).create();
        }

        // Create a pod to pull images. This is just to make things faster during test time
        imagePullerJob = new JobBuilder()
                .withNewMetadata()
                    .withName("pull-images")
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(1)
                    .withNewTemplate()
                        .withNewSpec()
                            .withRestartPolicy("Never")
                            .withContainers(
                                    new ContainerBuilder()
                                            .withName("database")
                                            .withImage(dbImage)
                                            .withCommand("echo")
                                            .withArgs("Database image pulled")
                                    .build(),
                                    new ContainerBuilder()
                                            .withName("backend")
                                            .withImage(serverImage)
                                            .withCommand("echo")
                                            .withArgs("Backend image pulled")
                                            .build(),
                                    new ContainerBuilder()
                                            .withName("ui")
                                            .withImage(uiImage)
                                            .withCommand("echo")
                                            .withArgs("UI image pulled")
                                            .build()
                            )
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        if (client.resource(imagePullerJob).inNamespace(getNamespaceName()).get() == null) {
            client.batch().v1().jobs().inNamespace(getNamespaceName()).resource(imagePullerJob).create();
        }

        // Start the operator
        operator.start();
    }

    @AfterEach
    public void afterEach() throws InterruptedException {
        if (resources != null || trustify != null) {
            if (resources != null) {
                resources.delete();
            }
            if (trustify != null) {
                client.resource(trustify)
                        .inNamespace(getNamespaceName())
                        .delete();
            }
            Thread.sleep(2_000);
        }
    }

    protected String getNamespaceName() {
        return namespace.getMetadata().getName();
    }

    protected Trustify generateTrustify(String name) {
        Trustify trustify = new Trustify();
        trustify.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(getNamespaceName())
                .build()
        );
        return trustify;
    }

    protected void createTrustify(Trustify trustify) throws InterruptedException {
        if (client.resource(trustify).inNamespace(getNamespaceName()) != null) {
            client.resource(trustify)
                    .inNamespace(getNamespaceName())
                    .delete();
            Thread.sleep(2_000);
        }
        this.trustify = client.resource(trustify)
                .inNamespace(getNamespaceName())
                .create();
    }

    protected void createResources(ListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> resources) throws InterruptedException {
        if (resources.resources().anyMatch(Objects::nonNull)) {
            resources.delete();
            Thread.sleep(2_000);
        }
        resources.create();

        this.resources = resources;
    }

    protected void verifyDatabase(Trustify cr) {
        // Database
        final var dbDeployment = client.apps()
                .deployments()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(DBDeployment.getDeploymentName(cr))
                .get();
        final var dbContainer = dbDeployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .stream()
                .findFirst();
        MatcherAssert.assertThat("DB container not found", dbContainer.isPresent(), Matchers.is(true));
        MatcherAssert.assertThat("DB container image not valid", dbContainer.get().getImage(), Matchers.is(dbImage));

        Assertions.assertEquals(1, dbDeployment.getStatus().getReadyReplicas(), "Expected DB deployment number of replicas doesn't match");

        // Database service
        final var dbService = client.services()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(DBService.getServiceName(cr))
                .get();
        final var dbPort = dbService.getSpec()
                .getPorts()
                .get(0)
                .getPort();
        MatcherAssert.assertThat("DB service port not valid", dbPort, Matchers.is(5432));
    }

    protected void verifyServer(Trustify cr) {
        // Server Deployment
        final var serverDeployment = client.apps()
                .deployments()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(ServerDeployment.getDeploymentName(cr))
                .get();
        final var serverContainer = serverDeployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .stream()
                .findFirst();
        MatcherAssert.assertThat("Server container not found", serverContainer.isPresent(), Matchers.is(true));
        MatcherAssert.assertThat("Server container image not valid", serverContainer.get().getImage(), Matchers.is(serverImage));
        List<Integer> serverContainerPorts = serverContainer.get().getPorts().stream()
                .map(ContainerPort::getContainerPort)
                .toList();
        Assertions.assertTrue(serverContainerPorts.contains(8080), "Server container port 8080 not found");

        Assertions.assertEquals(1, serverDeployment.getStatus().getAvailableReplicas(), "Expected Server deployment number of replicas doesn't match");

        // Server service
        final var serverService = client.services()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(ServerService.getServiceName(cr))
                .get();
        final var serverServicePorts = serverService.getSpec()
                .getPorts()
                .stream()
                .map(ServicePort::getPort)
                .toList();
        Assertions.assertTrue(serverServicePorts.contains(8080), "Server service port not valid");
    }

    protected void verifyUI(Trustify cr) {
        // UI Deployment
        final var uiDeployment = client.apps()
                .deployments()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(UIDeployment.getDeploymentName(cr))
                .get();
        final var uiContainer = uiDeployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .stream()
                .findFirst();
        MatcherAssert.assertThat("UI container not found", uiContainer.isPresent(), Matchers.is(true));
        MatcherAssert.assertThat("UI container image not valid", uiContainer.get().getImage(), Matchers.is(uiImage));
        List<Integer> uiContainerPorts = uiContainer.get().getPorts().stream()
                .map(ContainerPort::getContainerPort)
                .toList();
        Assertions.assertTrue(uiContainerPorts.contains(8080), "UI container port 8080 not found");

        Assertions.assertEquals(1, uiDeployment.getStatus().getAvailableReplicas(), "Expected UI deployment number of replicas doesn't match");

        // Server service
        final var uiService = client.services()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(UIService.getServiceName(cr))
                .get();
        final var uiServicePorts = uiService.getSpec()
                .getPorts()
                .stream()
                .map(ServicePort::getPort)
                .toList();
        Assertions.assertTrue(uiServicePorts.contains(8080), "UI service port not valid");
    }

    protected void verifyIngress(Trustify cr) {
        // Ingress
        final var ingress = client.network().v1().ingresses()
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(AppIngress.getIngressName(cr))
                .get();

        final var rules = ingress.getSpec().getRules();
        MatcherAssert.assertThat(rules.size(), Matchers.is(1));

        final var paths = rules.get(0).getHttp().getPaths();
        MatcherAssert.assertThat(paths.size(), Matchers.is(1));

        final var path = paths.get(0);

        final var serviceBackend = path.getBackend().getService();
        MatcherAssert.assertThat(serviceBackend.getName(), Matchers.is(UIService.getServiceName(cr)));
        MatcherAssert.assertThat(serviceBackend.getPort().getNumber(), Matchers.is(8080));
    }
}