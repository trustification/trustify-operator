package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.db.DBService;
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIIngress;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.db.DBService;
import org.trustify.operator.cdrs.v2alpha1.ui.UIService;
import org.trustify.operator.controllers.setup.K3sResource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class TrustifyReconcilerTest {

    public static final String TEST_APP = "myapp";

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

    Namespace namespace = null;
    Pod imagePullerPod = null;

    @BeforeEach
    public void startOperator() {
        namespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName(client.getNamespace())
                .endMetadata()
                .build();
        client.resource(namespace).create();

        imagePullerPod = new PodBuilder()
                .withNewMetadata()
                    .withName("pre-pull")
                    .endMetadata()
                .withNewSpec()
                    .addNewContainer()
                        .withName("database")
                        .withImage(dbImage)
                        .withCommand("echo", "Image pulled successfully")
                    .endContainer()
                    .addNewContainer()
                        .withName("server")
                        .withImage(serverImage)
                        .withCommand("--help")
                    .endContainer()
                    .addNewContainer()
                        .withName("ui")
                        .withImage(uiImage)
                    .endContainer()
                .endSpec()
                .build();
        client.resource(imagePullerPod).inNamespace(client.getNamespace()).create();

        operator.start();
    }

    @AfterEach
    public void stopOperator() {
        if (imagePullerPod != null) {
            client.resource(imagePullerPod)
                    .inNamespace(client.getNamespace())
                    .delete();
        }

        operator.stop();
    }

    @Test
    @Order(1)
    public void reconcileShouldWork() throws InterruptedException {
        final Trustify trustify = new Trustify();
        trustify.setMetadata(new ObjectMetaBuilder()
                .withName(TEST_APP)
                .withNamespace(namespace.getMetadata().getName())
                .build()
        );

        // Delete prev instance if exists already
        if (client.resource(trustify).inNamespace(trustify.getMetadata().getNamespace()).get() != null) {
            client.resource(trustify).inNamespace(trustify.getMetadata().getNamespace()).delete();
            Thread.sleep(10_000);
        }

        // Instantiate Trustify
        client.resource(trustify).inNamespace(trustify.getMetadata().getNamespace()).serverSideApply();

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify);
                });
    }

    public void verifyDatabase(Trustify cr) {
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

    public void verifyServer(Trustify cr) {
        List<Pod> items = client.pods()
                .inNamespace(cr.getMetadata().getNamespace())
                .list()
                .getItems();
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

    public void verifyUI(Trustify cr) {
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
                .inNamespace(client.getNamespace())
                .withName(UIService.getServiceName(cr))
                .get();
        final var uiServicePorts = uiService.getSpec()
                .getPorts()
                .stream()
                .map(ServicePort::getPort)
                .toList();
        Assertions.assertTrue(uiServicePorts.contains(8080), "UI service port not valid");
    }

    public void verifyIngress(Trustify cr) {
        // Ingress
        final var ingress = client.network().v1().ingresses()
                .inNamespace(client.getNamespace())
                .withName(UIIngress.getIngressName(cr))
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