#!/bin/bash

# This script renders the k8s service account secrets for namespace access
# NAMESPACE: wsmtest, ichang, other personal or preview namespaces.
NAMESPACE=${1}
if [ -z "$1" ]
  then
    echo "Please provide a namespace as input argument as in the following example."
    echo "Usage: ./render-k8s-config.sh <namespace: e.g. wsmtest>"
    exit 1;
fi
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}
TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/${NAMESPACE}/testrunner-k8s-sa
TESTRUNNER_K8S_SERVICE_ACCOUNT_CA_OUTPUT_PATH=$(dirname "$0")/rendered/testrunner-k8s-${NAMESPACE}-sa-client-key-data.crt
TESTRUNNER_K8S_SERVICE_ACCOUNT_TOKEN_OUTPUT_PATH=$(dirname "$0")/rendered/testrunner-k8s-${NAMESPACE}-sa-token

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json "${TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH}" | \
    jq -r .data.key | base64 --decode | \
    jq -r ".data[\"ca.crt\"]" > "${TESTRUNNER_K8S_SERVICE_ACCOUNT_CA_OUTPUT_PATH}"

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json "${TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH}" | \
    jq -r .data.key | base64 --decode | \
    jq -r .data.token | base64 --decode > "${TESTRUNNER_K8S_SERVICE_ACCOUNT_TOKEN_OUTPUT_PATH}"
