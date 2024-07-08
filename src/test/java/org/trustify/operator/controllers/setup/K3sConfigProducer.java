package org.trustify.operator.controllers.setup;

import io.fabric8.kubernetes.client.Config;
import io.quarkus.kubernetes.client.runtime.KubernetesClientBuildConfig;
import io.quarkus.kubernetes.client.runtime.KubernetesConfigProducer;
import io.quarkus.runtime.TlsConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Alternative
@Priority(1)
@Singleton
public class K3sConfigProducer extends KubernetesConfigProducer {
    //Injects the kubeConfigYaml that you've set in the K3sResource
    @ConfigProperty(name = "kubeConfigYaml")
    String kubeConfigYaml;

    @ConfigProperty(name = "quarkus.kubernetes.namespace")
    String namespace;

    //Returns the kubeConfigYaml as the config
    @Singleton
    @Produces
    public Config config(KubernetesClientBuildConfig buildConfig, TlsConfig tlsConfig) {
        String kubeConfigYamlWithDefaultNamespace = kubeConfigYaml.replace("""
                    user: "default"
                """, """
                    user: "default"
                    namespace: "%s"
                """.formatted(namespace));
        return Config.fromKubeconfig(kubeConfigYamlWithDefaultNamespace);
    }
}
