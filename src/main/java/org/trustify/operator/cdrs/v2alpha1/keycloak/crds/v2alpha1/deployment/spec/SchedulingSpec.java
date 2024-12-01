package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraint;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchedulingSpec {

    private Affinity affinity;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Toleration> tolerations = new ArrayList<Toleration>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<TopologySpreadConstraint> topologySpreadConstraints = new ArrayList<TopologySpreadConstraint>();
    private String priorityClassName;

    public Affinity getAffinity() {
        return affinity;
    }

    public void setAffinity(Affinity affinity) {
        this.affinity = affinity;
    }

    public List<Toleration> getTolerations() {
        return tolerations;
    }

    public void setTolerations(List<Toleration> tolerations) {
        this.tolerations = tolerations;
    }

    public List<TopologySpreadConstraint> getTopologySpreadConstraints() {
        return topologySpreadConstraints;
    }

    public void setTopologySpreadConstraints(List<TopologySpreadConstraint> topologySpreadConstraints) {
        this.topologySpreadConstraints = topologySpreadConstraints;
    }

    public String getPriorityClassName() {
        return priorityClassName;
    }

    public void setPriorityClassName(String priorityClassName) {
        this.priorityClassName = priorityClassName;
    }

}
