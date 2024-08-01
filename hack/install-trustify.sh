#!/bin/bash

set -e
set -x

# Figure out where we are being run from.
# This relies on script being run from:
#  - ${PROJECT_ROOT}/hack/install-trustify.sh
#  - ${PROJECT_ROOT}/bin/install-trustify.sh
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__root="$(cd "$(dirname "${__dir}")" && pwd)"
__repo="$(basename "${__root}")"
__bin_dir="${__root}/bin"
__os="$(uname -s | tr '[:upper:]' '[:lower:]')"
__arch="$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/')"

# Update PATH for execution of this script
export PATH="${__bin_dir}:${PATH}"

NAMESPACE="${NAMESPACE:-trustify}"
OPERATOR_BUNDLE_IMAGE="${OPERATOR_BUNDLE_IMAGE:-ghcr.io/trustification/trustify-operator-bundle:latest}"
SERVER_IMAGE="${SERVER_IMAGE:-ghcr.io/trustification/trustd:latest}"
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY:-Always}"
TIMEOUT="${TIMEOUT:-15m}"

if ! command -v kubectl >/dev/null 2>&1; then
  kubectl_bin="${__bin_dir}/kubectl"
  mkdir -p "${__bin_dir}"
  curl -Lo "${kubectl_bin}" "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/${__os}/${__arch}/kubectl"
  chmod +x "${kubectl_bin}"
fi

if ! command -v operator-sdk1 >/dev/null 2>&1; then
  operator_sdk_bin="${__bin_dir}/operator-sdk"
  mkdir -p "${__bin_dir}"

  version=$(curl --silent "https://api.github.com/repos/operator-framework/operator-sdk/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
  curl -Lo "${operator_sdk_bin}" "https://github.com/operator-framework/operator-sdk/releases/download/${version}/operator-sdk_${__os}_${__arch}"
  chmod +x "${operator_sdk_bin}"
fi

install_operator() {
  kubectl auth can-i create namespace --all-namespaces
  kubectl create namespace ${NAMESPACE} || true
  operator-sdk run bundle "${OPERATOR_BUNDLE_IMAGE}" --namespace "${NAMESPACE}" --timeout "${TIMEOUT}"

  # If on MacOS, need to install `brew install coreutils` to get `timeout`
  timeout 600s bash -c 'until kubectl get customresourcedefinitions.apiextensions.k8s.io trustifies.org.trustify; do sleep 30; done' \
  || kubectl get subscription --namespace ${NAMESPACE} -o yaml trustify-operator # Print subscription details when timed out
}

kubectl get customresourcedefinitions.apiextensions.k8s.io clusterserviceversions.operators.coreos.com || operator-sdk olm install
olm_namespace=$(kubectl get clusterserviceversions.operators.coreos.com --all-namespaces | grep packageserver | awk '{print $1}')
kubectl rollout status -w deployment/olm-operator --namespace="${olm_namespace}"
kubectl rollout status -w deployment/catalog-operator --namespace="${olm_namespace}"
kubectl wait --namespace "${olm_namespace}" --for='jsonpath={.status.phase}'=Succeeded clusterserviceversions.operators.coreos.com packageserver
kubectl get customresourcedefinitions.apiextensions.k8s.io org.trustify || install_operator


# Create, and wait for, trustify
kubectl wait \
  --namespace ${NAMESPACE} \
  --for=condition=established \
  customresourcedefinitions.apiextensions.k8s.io/trustifies.org.trustify
cat <<EOF | kubectl apply -f -
kind: Trustify
apiVersion: org.trustify/v1alpha1
metadata:
  name: myapp
  namespace: ${NAMESPACE}
spec:
  serverImage: ${SERVER_IMAGE}
  imagePullPolicy: ${IMAGE_PULL_POLICY}
EOF
# Wait for reconcile to finish
kubectl wait \
  --namespace ${NAMESPACE} \
  --for=condition=Successful \
  --timeout=600s \
  trustifies.org.trustify/myapp \
|| kubectl get \
  --namespace ${NAMESPACE} \
  -o yaml \
  trustifies.org.trustify/myapp # Print trustify debug when timed out

# Now wait for all the trustify deployments
kubectl wait \
  --namespace ${NAMESPACE} \
  --selector="app.kubernetes.io/part-of=myapp" \
  --for=condition=Available \
  --timeout=600s \
  deployments.apps \
|| kubectl get \
  --namespace ${NAMESPACE} \
  --selector="app.kubernetes.io/part-of=myapp" \
  --field-selector=status.phase!=Running  \
  -o yaml \
  pods # Print not running trustify pods when timed out