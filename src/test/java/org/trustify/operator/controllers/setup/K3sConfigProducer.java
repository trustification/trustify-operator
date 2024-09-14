package org.trustify.operator.controllers.setup;

import io.fabric8.kubernetes.client.Config;
import io.quarkus.kubernetes.client.runtime.KubernetesClientBuildConfig;
import io.quarkus.kubernetes.client.runtime.KubernetesConfigProducer;
import io.quarkus.tls.runtime.config.TlsConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.*;

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
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    @Singleton
    @Produces
    public Config config(KubernetesClientBuildConfig buildConfig, TlsConfig tlsConfig) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> yaml = mapper.readValue(kubeConfigYaml, Map.class);

            Optional.ofNullable(yaml.get("current-context"))
                    .flatMap(currentContext -> ((List) yaml.getOrDefault("contexts", Collections.emptyMap()))
                            .stream()
                            .filter(context -> Objects.equals(((Map) context).get("name"), currentContext))
                            .findAny()
                    )
                    .ifPresent(context -> {
                        Map<String, String> ctxConfig = (Map) ((Map<String, Map>) context).get("context");
                        ctxConfig.put("namespace", namespace);
                    });

            String kubeConfigYamlWithDefaultNamespace = mapper.writeValueAsString(yaml);
            return Config.fromKubeconfig(kubeConfigYamlWithDefaultNamespace);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
