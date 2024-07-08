package org.trustify.operator.controllers.setup;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;

public class K3sResource implements QuarkusTestResourceLifecycleManager {
    static K3sContainer k3sContainer = new K3sContainer(DockerImageName.parse("rancher/k3s:" + Optional.ofNullable(System.getenv("KUBERNETES_VERSION")).orElse("latest")));

    @Override
    public Map<String, String> start() {
        k3sContainer.start();
        return Map.of(
                "kubeConfigYaml", k3sContainer.getKubeConfigYaml(),
                "quarkus.kubernetes.namespace", "trustify-operator"
        );
    }

    @Override
    public void stop() {
        k3sContainer.stop();
    }
}
