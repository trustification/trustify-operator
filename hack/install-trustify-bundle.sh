#!/bin/bash

set -e
set -x
set -o pipefail

NAMESPACE="${NAMESPACE:-trustify}"
OPERATOR_BUNDLE_IMAGE="${OPERATOR_BUNDLE_IMAGE:-ghcr.io/trustification/trustify-operator-bundle:latest}"
TRUSTIFY_CR="${TRUSTIFY_CR:-}"
TIMEOUT="${TIMEOUT:-15m}"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "Please install kubectl. See https://kubernetes.io/docs/tasks/tools/"
  exit 1
fi

if ! command -v operator-sdk >/dev/null 2>&1; then
  echo "Please install operator-sdk. See https://sdk.operatorframework.io/docs/installation/"
  exit 1
fi

debug() {
  echo "Install Trustify FAILED!!!"
  echo "What follows is some info that may be useful in debugging the failure"

  kubectl get namespace "${NAMESPACE}" -o yaml || true
  kubectl get --namespace "${NAMESPACE}" all || true
  kubectl get --namespace "${NAMESPACE}" -o yaml \
    subscriptions.operators.coreos.com,catalogsources.operators.coreos.com,installplans.operators.coreos.com,clusterserviceversions.operators.coreos.com \
    || true
  kubectl get --namespace "${NAMESPACE}" -o yaml trustifies.org.trustify/myapp || true

  for pod in $(kubectl get pods -n "${NAMESPACE}" -o jsonpath='{.items[*].metadata.name}'); do
    kubectl --namespace "${NAMESPACE}" describe pod "${pod}" || true
  done
  exit 1
}
trap 'debug' ERR

run_bundle() {
  kubectl auth can-i create namespace --all-namespaces
  kubectl create namespace "${NAMESPACE}" || true
  operator-sdk run bundle "${OPERATOR_BUNDLE_IMAGE}" --namespace "${NAMESPACE}" --timeout "${TIMEOUT}"

  # If on MacOS, need to install `brew install coreutils` to get `timeout`
  timeout 600s bash -c 'until kubectl get customresourcedefinitions.apiextensions.k8s.io trustifies.org.trustify; do sleep 30; done'
  kubectl get clusterserviceversions.operators.coreos.com -n "${NAMESPACE}" -o yaml
}

install_trustify() {
  echo "Waiting for the Trustify CRD to become available"
  kubectl wait --namespace "${NAMESPACE}" --for=condition=established customresourcedefinitions.apiextensions.k8s.io/trustifies.org.trustify

  echo "Waiting for the Trustify Operator to exist"
  timeout 2m bash -c "until kubectl --namespace ${NAMESPACE} get deployment/trustify-operator; do sleep 10; done"

  echo "Waiting for the Trustify Operator to become available"
  kubectl rollout status --namespace "${NAMESPACE}" -w deployment/trustify-operator --timeout=600s

  if [ -n "${TRUSTIFY_CR}" ]; then
    echo "${TRUSTIFY_CR}" | kubectl apply --namespace "${NAMESPACE}" -f -
  else
    cat <<EOF | kubectl apply --namespace "${NAMESPACE}" -f -
kind: Trustify
apiVersion: org.trustify/v1alpha1
metadata:
  name: myapp
spec: {}
EOF
  fi

  # Want to see in github logs what we just created
  kubectl get --namespace "${NAMESPACE}" -o yaml trustifies.org.trustify/myapp

  # Wait for reconcile to finish
  kubectl wait \
    --namespace "${NAMESPACE}" \
    --for=condition=Successful \
    --timeout=600s \
    trustifies.org.trustify/myapp

  # Now wait for all the deployments
  kubectl wait \
    --namespace "${NAMESPACE}" \
    --selector="app.kubernetes.io/part-of=myapp" \
    --for=condition=Available \
    --timeout=600s \
    deployments.apps

  kubectl get deployments.apps -n "${NAMESPACE}" -o yaml
}

kubectl get customresourcedefinitions.apiextensions.k8s.io clusterserviceversions.operators.coreos.com || operator-sdk olm install
olm_namespace=$(kubectl get clusterserviceversions.operators.coreos.com --all-namespaces | grep packageserver | awk '{print $1}')
kubectl rollout status -w deployment/olm-operator --namespace="${olm_namespace}"
kubectl rollout status -w deployment/catalog-operator --namespace="${olm_namespace}"
kubectl wait --namespace "${olm_namespace}" --for='jsonpath={.status.phase}'=Succeeded clusterserviceversions.operators.coreos.com packageserver
kubectl get customresourcedefinitions.apiextensions.k8s.io org.trustify || run_bundle
install_trustify