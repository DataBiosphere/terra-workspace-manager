#!/bin/bash

WSM_ENV=${1:-local}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

if [ -z "${WSM_ENV}" ]; then
    echo "ENV not defined."
    exit 1
elif ! [[  "${WSM_ENV}" = "local" || "${WSM_ENV}" = "dev" ||  "${WSM_ENV}" = "alpha" || "${WSM_ENV}" = "staging" ]]; then
    echo "${WSM_ENV} not supported."
    exit 1
elif [ "${WSM_ENV}" = "local" ]; then
  ENV=dev
  WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/wsmtest/workspace/app-sa
else
  WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/${WSM_ENV}/${WSM_ENV}/workspace/app-sa
fi


WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/service-account.json
USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/${WSM_ENV}/common/firecloud-account.json
USER_DELEGATED_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/user-delegated-service-account.json
JANITOR_CLIENT_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/tools/crl_janitor/client-sa
JANITOR_CLIENT_SERVICE_ACCOUNT_OUTPUT_PATH="$(dirname $0)"/rendered/janitor-client-sa-account.json
BUFFER_CLIENT_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/tools/buffer/client-sa
BUFFER_CLIENT_SERVICE_ACCOUNT_OUTPUT_PATH="$(dirname $0)"/rendered/buffer-client-sa-account.json

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${WM_APP_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 -d > ${WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH} | \
    # Not base64 encoded or stored under 'key'
    jq -r .data > ${USER_DELEGATED_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${JANITOR_CLIENT_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 -d > ${JANITOR_CLIENT_SERVICE_ACCOUNT_OUTPUT_PATH}
docker run --rm -e VAULT_TOKEN="$VAULT_TOKEN" $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault read -format=json ${BUFFER_CLIENT_SERVICE_ACCOUNT_VAULT_PATH} | \
    jq -r .data.key | base64 -d > ${BUFFER_CLIENT_SERVICE_ACCOUNT_OUTPUT_PATH}
