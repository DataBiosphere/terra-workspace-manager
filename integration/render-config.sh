#!/bin/bash

VAULT_TOKEN=${1:-$(cat "$HOME"/.vault-token)}
WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/wsmtest/workspace/app-sa
WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/workspace-manager-app-service-account.json
USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/dev/common/firecloud-account.json
USER_DELEGATED_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/user-delegated-service-account.json
TESTRUNNER_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/common/testrunner/testrunner-sa
TESTRUNNER_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/testrunner-service-account.json

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${WM_APP_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 --decode > ${WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH} | \
    # Not base64 encoded or stored under 'key'
    jq -r .data > ${USER_DELEGATED_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${TESTRUNNER_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 --decode > ${TESTRUNNER_SERVICE_ACCOUNT_OUTPUT_PATH}
