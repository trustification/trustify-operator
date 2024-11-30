package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIIngress;
import org.trustify.operator.cdrs.v2alpha1.ui.UIService;
import org.trustify.operator.controllers.setup.K3sResource;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class HostnameSpecTest extends ReconcilerBaseTest {

    @Test
    public void customHostname() throws InterruptedException {
        String host = "app1.example.com";

        // Create
        final Trustify trustify = generateTrustify("custom-hostname");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.HostnameSpec(
                        host
                ),
                null,
                null,
                null,
                null,
                null
        ));

        createTrustify(trustify);

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify);

                    // Ingress
                    final var ingress = client.network().v1().ingresses()
                            .inNamespace(client.getNamespace())
                            .withName(UIIngress.getIngressName(trustify))
                            .get();

                    final var rules = ingress.getSpec().getRules();
                    Assertions.assertEquals(host, rules.getFirst().getHost());
                });
    }

}