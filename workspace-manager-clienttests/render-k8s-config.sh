#!/bin/bash

# This script renders the k8s service account secrets for namespace access
# NAME_SPACE: ichang, zloery, or preview namespace etc.
NAME_SPACE=${1}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}
TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/${NAME_SPACE}/testrunner-k8s-sa
TESTRUNNER_K8S_SERVICE_ACCOUNT_CA_OUTPUT_PATH=$(dirname "$0")/rendered/testrunner-k8s-sa-client-key-data.crt
TESTRUNNER_K8S_SERVICE_ACCOUNT_TOKEN_OUTPUT_PATH=$(dirname "$0")/rendered/testrunner-k8s-sa-token

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 --decode | \
    jq -r ".data[\"ca.crt\"]" > ${TESTRUNNER_K8S_SERVICE_ACCOUNT_CA_OUTPUT_PATH}

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 --decode | \
    jq -r .data.token | base64 --decode > ${TESTRUNNER_K8S_SERVICE_ACCOUNT_TOKEN_OUTPUT_PATH}