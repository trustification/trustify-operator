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
import org.trustify.operator.cdrs.v2alpha1.api.ApiDeployment;
import org.trustify.operator.cdrs.v2alpha1.api.ApiIngress;
import org.trustify.operator.cdrs.v2alpha1.api.ApiService;
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
    String apiImage;

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


                    // Api Deployment
                    final var apiDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(ApiDeployment.getDeploymentName(app))
                            .get();
                    final var webContainer = apiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .stream()
                            .findFirst();
                    MatcherAssert.assertThat("Api container not found", webContainer.isPresent(), Matchers.is(true));
                    MatcherAssert.assertThat("Api container image not valid", webContainer.get().getImage(), Matchers.is(apiImage));
                    List<Integer> webContainerPorts = webContainer.get().getPorts().stream()
                            .map(ContainerPort::getContainerPort)
                            .toList();
                    Assertions.assertTrue(webContainerPorts.contains(8080), "Api container port 8080 not found");

                    Assertions.assertEquals(1, apiDeployment.getStatus().getReadyReplicas(), "Expected Api deployment number of replicas doesn't match");

                    // Api service
                    final var apiService = client.services()
                            .inNamespace(metadata.getNamespace())
                            .withName(ApiService.getServiceName(app))
                            .get();
                    final var apiServicePorts = apiService.getSpec()
                            .getPorts()
                            .stream()
                            .map(ServicePort::getPort)
                            .toList();
                    Assertions.assertTrue(apiServicePorts.contains(8080), "Api service port not valid");

                    // Ingress
                    final var ingress = client.network().v1().ingresses()
                            .inNamespace(metadata.getNamespace())
                            .withName(ApiIngress.getIngressName(app))
                            .get();

                    final var rules = ingress.getSpec().getRules();
                    MatcherAssert.assertThat(rules.size(), Matchers.is(1));

                    final var paths = rules.get(0).getHttp().getPaths();
                    MatcherAssert.assertThat(paths.size(), Matchers.is(1));

                    final var path = paths.get(0);

                    final var serviceBackend = path.getBackend().getService();
                    MatcherAssert.assertThat(serviceBackend.getName(), Matchers.is(ApiService.getServiceName(app)));
                    MatcherAssert.assertThat(serviceBackend.getPort().getNumber(), Matchers.is(8080));
                });
    }
}