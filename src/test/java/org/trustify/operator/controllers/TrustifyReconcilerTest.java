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
import org.trustify.operator.cdrs.v2alpha1.*;
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.ServerIngress;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.db.DBService;
import org.trustify.operator.controllers.setup.K3sResource;

import java.util.List;
import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class TrustifyReconcilerTest {

    public static final String TEST_APP = "myapp";

    @ConfigProperty(name = "related.image.db")
    String dbImage;

    @ConfigProperty(name = "related.image.server")
    String serverImage;

    @Inject
    KubernetesClient client;

    @Inject
    Operator operator;

    @BeforeEach
    public void startOperator() {
        operator.start();
    }

    @AfterEach
    public void stopOperator() {
        operator.stop();
    }

    @Test
    @Order(1)
    public void reconcileShouldWork() throws InterruptedException {
        // Requirements
        client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(client.getNamespace())
                        .endMetadata()
                        .build()
                )
                .create();

        client.resource(new ServiceAccountBuilder()
                        .withNewMetadata()
                        .withName(Constants.TRUSTI_NAME)
                        .endMetadata()
                        .build()
                )
                .inNamespace(client.getNamespace())
                .create();

        //
        final var app = new Trustify();
        final var metadata = new ObjectMetaBuilder()
                .withName(TEST_APP)
                .withNamespace(client.getNamespace())
                .build();
        app.setMetadata(metadata);

        // Delete prev instance if exists already
        if (client.resource(app).inNamespace(metadata.getNamespace()).get() != null) {
            client.resource(app).inNamespace(metadata.getNamespace()).delete();
            Thread.sleep(10_000);
        }

        // Instantiate Trusti
        client.resource(app).inNamespace(metadata.getNamespace()).serverSideApply();

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    // Database
                    final var dbDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(DBDeployment.getDeploymentName(app))
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
                            .inNamespace(metadata.getNamespace())
                            .withName(DBService.getServiceName(app))
                            .get();
                    final var dbPort = dbService.getSpec()
                            .getPorts()
                            .get(0)
                            .getPort();
                    MatcherAssert.assertThat("DB service port not valid", dbPort, Matchers.is(5432));


                    // Server Deployment
                    final var serverDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(ServerDeployment.getDeploymentName(app))
                            .get();
                    final var webContainer = serverDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .stream()
                            .findFirst();
                    MatcherAssert.assertThat("Server container not found", webContainer.isPresent(), Matchers.is(true));
                    MatcherAssert.assertThat("Server container image not valid", webContainer.get().getImage(), Matchers.is(serverImage));
                    List<Integer> webContainerPorts = webContainer.get().getPorts().stream()
                            .map(ContainerPort::getContainerPort)
                            .toList();
                    Assertions.assertTrue(webContainerPorts.contains(8080), "Server container port 8080 not found");

                    Assertions.assertEquals(1, serverDeployment.getStatus().getReadyReplicas(), "Expected Server deployment number of replicas doesn't match");

                    // Server service
                    final var serverService = client.services()
                            .inNamespace(metadata.getNamespace())
                            .withName(ServerService.getServiceName(app))
                            .get();
                    final var serverServicePorts = serverService.getSpec()
                            .getPorts()
                            .stream()
                            .map(ServicePort::getPort)
                            .toList();
                    Assertions.assertTrue(serverServicePorts.contains(8080), "Server service port not valid");

                    // Ingress
                    final var ingress = client.network().v1().ingresses()
                            .inNamespace(metadata.getNamespace())
                            .withName(ServerIngress.getIngressName(app))
                            .get();

                    final var rules = ingress.getSpec().getRules();
                    MatcherAssert.assertThat(rules.size(), Matchers.is(1));

                    final var paths = rules.get(0).getHttp().getPaths();
                    MatcherAssert.assertThat(paths.size(), Matchers.is(1));

                    final var path = paths.get(0);

                    final var serviceBackend = path.getBackend().getService();
                    MatcherAssert.assertThat(serviceBackend.getName(), Matchers.is(ServerService.getServiceName(app)));
                    MatcherAssert.assertThat(serviceBackend.getPort().getNumber(), Matchers.is(8080));
                });
    }
}