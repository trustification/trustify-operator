package org.trustify.operator.cdrs.v2alpha1.keycloak.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.model.annotation.SpecReplicas;
import org.trustify.operator.ValueOrSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeycloakSpec {

    @SpecReplicas
    @JsonPropertyDescription("Number of Keycloak instances. Default is 1.")
    private Integer instances;

    @JsonPropertyDescription("Custom Keycloak image to be used.")
    private String image;

    @JsonPropertyDescription("Set to force the behavior of the --optimized flag for the start command. If left unspecified the operator will assume custom images have already been augmented.")
    private Boolean startOptimized;

    @JsonPropertyDescription("Secret(s) that might be used when pulling an image from a private container image registry or repository.")
    private List<LocalObjectReference> imagePullSecrets;

    @JsonPropertyDescription("Configuration of the Keycloak server.\n" +
            "expressed as a keys (reference: https://www.keycloak.org/server/all-config) and values that can be either direct values or references to secrets.")
    private List<ValueOrSecret> additionalOptions; // can't use Set due to a bug in Sundrio https://github.com/sundrio/sundrio/issues/316

    @JsonProperty("http")
    @JsonPropertyDescription("In this section you can configure Keycloak features related to HTTP and HTTPS")
    private HttpSpec httpSpec;

    @JsonProperty("ingress")
    @JsonPropertyDescription("The deployment is, by default, exposed through a basic ingress.\n" +
            "You can change this behaviour by setting the enabled property to false.")
    private IngressSpec ingressSpec;

    @JsonProperty("features")
    @JsonPropertyDescription("In this section you can configure Keycloak features, which should be enabled/disabled.")
    private FeatureSpec featureSpec;

    @JsonProperty("transaction")
    @JsonPropertyDescription("In this section you can find all properties related to the settings of transaction behavior.")
    private TransactionsSpec transactionsSpec;

    @JsonProperty("db")
    @JsonPropertyDescription("In this section you can find all properties related to connect to a database.")
    private DatabaseSpec databaseSpec;

    @JsonProperty("hostname")
    @JsonPropertyDescription("In this section you can configure Keycloak hostname and related properties.")
    private HostnameSpec hostnameSpec;

    @JsonPropertyDescription("In this section you can configure Keycloak truststores.")
    private Map<String, Truststore> truststores = new LinkedHashMap<>();

    @JsonProperty("cache")
    @JsonPropertyDescription("In this section you can configure Keycloak's cache")
    private CacheSpec cacheSpec;

    @JsonProperty("resources")
    @JsonPropertyDescription("Compute Resources required by Keycloak container")
    private ResourceRequirements resourceRequirements;

    @JsonProperty("proxy")
    @JsonPropertyDescription("In this section you can configure Keycloak's reverse proxy setting")
    private ProxySpec proxySpec;

    @JsonProperty("httpManagement")
    @JsonPropertyDescription("In this section you can configure Keycloak's management interface setting.")
    private HttpManagementSpec httpManagementSpec;

    @JsonProperty("scheduling")
    @JsonPropertyDescription("In this section you can configure Keycloak's scheduling")
    private SchedulingSpec schedulingSpec;

    @JsonProperty("bootstrapAdmin")
    @JsonPropertyDescription("In this section you can configure Keycloak's bootstrap admin - will be used only for inital cluster creation.")
    private BootstrapAdminSpec bootstrapAdminSpec;

    public HttpSpec getHttpSpec() {
        return httpSpec;
    }

    public void setHttpSpec(HttpSpec httpSpec) {
        this.httpSpec = httpSpec;
    }

    public FeatureSpec getFeatureSpec() {
        return featureSpec;
    }

    public void setFeatureSpec(FeatureSpec featureSpec) {
        this.featureSpec = featureSpec;
    }

    public TransactionsSpec getTransactionsSpec() {
        return transactionsSpec;
    }

    public void setTransactionsSpec(TransactionsSpec transactionsSpec) {
        this.transactionsSpec = transactionsSpec;
    }

    public IngressSpec getIngressSpec() {
        return ingressSpec;
    }

    public void setIngressSpec(IngressSpec ingressSpec) {
        this.ingressSpec = ingressSpec;
    }

    public DatabaseSpec getDatabaseSpec() {
        return databaseSpec;
    }

    public void setDatabaseSpec(DatabaseSpec databaseSpec) {
        this.databaseSpec = databaseSpec;
    }

    public HostnameSpec getHostnameSpec() {
        return hostnameSpec;
    }

    public void setHostnameSpec(HostnameSpec hostnameSpec) {
        this.hostnameSpec = hostnameSpec;
    }

    public Integer getInstances() {
        return instances;
    }

    public void setInstances(Integer instances) {
        this.instances = instances;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<LocalObjectReference> getImagePullSecrets() {
        return this.imagePullSecrets;
    }

    public void setImagePullSecrets(List<LocalObjectReference> imagePullSecrets) {
        this.imagePullSecrets = imagePullSecrets;
    }

    public HttpManagementSpec getHttpManagementSpec() {
        return httpManagementSpec;
    }

    public void setHttpManagementSpec(HttpManagementSpec httpManagementSpec) {
        this.httpManagementSpec = httpManagementSpec;
    }

    public List<ValueOrSecret> getAdditionalOptions() {
        if (this.additionalOptions == null) {
            this.additionalOptions = new ArrayList<>();
        }
        return additionalOptions;
    }

    public void setAdditionalOptions(List<ValueOrSecret> additionalOptions) {
        this.additionalOptions = additionalOptions;
    }

    public Boolean getStartOptimized() {
        return startOptimized;
    }

    public void setStartOptimized(Boolean optimized) {
        this.startOptimized = optimized;
    }

    public Map<String, Truststore> getTruststores() {
        return truststores;
    }

    public void setTruststores(Map<String, Truststore> truststores) {
        if (truststores == null) {
            truststores = new LinkedHashMap<>();
        }
        this.truststores = truststores;
    }

    public CacheSpec getCacheSpec() {
        return cacheSpec;
    }

    public void setCacheSpec(CacheSpec cache) {
        this.cacheSpec = cache;
    }

    public ResourceRequirements getResourceRequirements() {
        return resourceRequirements;
    }

    public void setResourceRequirements(ResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
    }

    public ProxySpec getProxySpec() {
        return proxySpec;
    }

    public void setProxySpec(ProxySpec proxySpec) {
        this.proxySpec = proxySpec;
    }

    public SchedulingSpec getSchedulingSpec() {
        return schedulingSpec;
    }

    public void setSchedulingSpec(SchedulingSpec schedulingSpec) {
        this.schedulingSpec = schedulingSpec;
    }

    public BootstrapAdminSpec getBootstrapAdminSpec() {
        return bootstrapAdminSpec;
    }

    public void setBootstrapAdminSpec(BootstrapAdminSpec bootstrapAdminSpec) {
        this.bootstrapAdminSpec = bootstrapAdminSpec;
    }
}