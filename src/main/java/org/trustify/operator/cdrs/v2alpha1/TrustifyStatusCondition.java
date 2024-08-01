package org.trustify.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrustifyStatusCondition {
    public static final String SUCCESSFUL = "Successful";
    public static final String PROCESSING = "Processing";
    public static final String DEGRADED = "Degraded";

    public enum Status {
        True,
        False,
        Unknown
    }

    private String type;
    private String status = Status.Unknown.name();

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("status")
    public String getStatusString() {
        return status;
    }

    @JsonProperty("status")
    public void setStatusString(String status) {
        this.status = status;
    }

    public Boolean getStatus() {
        if (status == null || Status.Unknown.name().equals(status)) {
            return null;
        }
        return Status.True.name().equals(status);
    }

    public void setStatus(Boolean status) {
        if (status == null) {
            this.status = Status.Unknown.name();
        } else if (status) {
            this.status = Status.True.name();
        } else {
            this.status = Status.False.name();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustifyStatusCondition that = (TrustifyStatusCondition) o;
        return Objects.equals(getType(), that.getType()) && Objects.equals(getStatus(), that.getStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), getStatus());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "type='" + type + '\'' +
                ", status=" + status +
                '}';
    }
}
