#!/bin/bash

ENV=${1:-local}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

if [ -z "${ENV}" ]; then
    echo "ENV not undefined."
    exit 1
elif ! [[ "${ENV}" = "dev" ||  "${ENV}" = "alpha" || "${ENV}" = "staging" ]]; then
    echo "ENV not supported."
    exit 1
fi

WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/${ENV}/${ENV}/workspace/app-sa
WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/workspace-manager-app-service-account.json
USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/${ENV}/common/firecloud-account.json
USER_DELEGATED_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/user-delegated-service-account.json
TESTRUNNER_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/dev/common/testrunner-sa
TESTRUNNER_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/testrunner-service-account.json

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${WM_APP_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 -d > ${WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH} | \
    # Not base64 encoded or stored under 'key'
    jq -r .data > ${USER_DELEGATED_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${TESTRUNNER_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 -d > ${TESTRUNNER_SERVICE_ACCOUNT_OUTPUT_PATH}
