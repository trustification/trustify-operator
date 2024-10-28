FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 AS build

ARG QUARKUS_OPTS
ARG CHANNELS=alpha

COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/
USER quarkus
WORKDIR /code
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY src/main /code/src/main
RUN ./mvnw package -DskipTests ${QUARKUS_OPTS} -Dquarkus.operator-sdk.bundle.channels=${CHANNELS}

FROM registry.access.redhat.com/ubi9/ubi:latest AS bundle
COPY scripts /scripts
COPY --from=build /code/target/bundle/trustify-operator/ /code/target/bundle/trustify-operator/
RUN dnf install wget --allowerasing -y && \
    wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/bin/yq && \
    chmod +x /usr/bin/yq && \
    # annotations.yaml \
    ANNOTATIONS_FILE=/code/target/bundle/trustify-operator/metadata/annotations.yaml && \
    yq e -P -i '.annotations."com.redhat.openshift.versions"="v4.10"' ${ANNOTATIONS_FILE} && \
    # clusterserviceversion.yaml \
    CSV_FILE=/code/target/bundle/trustify-operator/manifests/trustify-operator.clusterserviceversion.yaml && \
    yq e -P -i '.metadata.annotations.support = "https://github.com/trustification/trustify-operator/issues"' ${CSV_FILE} && \
    yq e -P -i '.metadata.annotations.description = "An Operator for installing and managing Trustify"' ${CSV_FILE} && \
    NOW_DATE=$(date --iso-8601=seconds) yq e -P -i '.metadata.annotations.createdAt = strenv(NOW_DATE)' ${CSV_FILE} && \
    yq e -P -i '.metadata.annotations.containerImage = .spec.install.spec.deployments[0].spec.template.spec.containers[0].image' ${CSV_FILE} && \
    yq e -P -i '.spec.customresourcedefinitions.owned[0].description = "Represents a Trustify instance"' ${CSV_FILE} && \
    yq e -P -i '.spec.customresourcedefinitions.owned[0].displayName = "Trustify"' ${CSV_FILE} && \
    yq e -P -i '.spec.install.spec.clusterPermissions[0].rules[0].apiGroups = ["apiextensions.k8s.io", "config.openshift.io"]' ${CSV_FILE} && \
    yq e -P -i '.spec.install.spec.clusterPermissions[0].rules[0].resources = ["customresourcedefinitions", "ingresses"]' ${CSV_FILE} && \
    yq e -P -i '.spec.install.spec.clusterPermissions[0].rules[0].verbs = ["get", "list"]' ${CSV_FILE}

FROM scratch
ARG CHANNELS=alpha

# Core bundle labels.
LABEL operators.operatorframework.io.bundle.channel.default.v1=${CHANNELS}
LABEL operators.operatorframework.io.bundle.channels.v1=${CHANNELS}
LABEL operators.operatorframework.io.bundle.manifests.v1=manifests/
LABEL operators.operatorframework.io.bundle.mediatype.v1=registry+v1
LABEL operators.operatorframework.io.bundle.metadata.v1=metadata/
LABEL operators.operatorframework.io.bundle.package.v1=trustify-operator
LABEL operators.operatorframework.io.metrics.builder=qosdk-bundle-generator/6.8.2+5def15d
LABEL operators.operatorframework.io.metrics.mediatype.v1=metrics+v1
LABEL operators.operatorframework.io.metrics.project_layout=quarkus.javaoperatorsdk.io/v1-alpha

# Copy files to locations specified by labels.
COPY --from=bundle /code/target/bundle/trustify-operator/manifests /manifests/
COPY --from=bundle /code/target/bundle/trustify-operator/metadata /metadata/
