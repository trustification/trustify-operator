package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CacheSpec {

    private ConfigMapKeySelector configMapFile;

    public ConfigMapKeySelector getConfigMapFile() {
        return configMapFile;
    }

    public void setConfigMapFile(ConfigMapKeySelector configMapFile) {
        this.configMapFile = configMapFile;
    }

}
