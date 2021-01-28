#!/bin/bash

ENV=${1:-local}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

if [ -z "${ENV}" ]; then
    echo "ENV not defined."
    exit 1
elif ! [[  "${ENV}" = "local" || "${ENV}" = "dev" ||  "${ENV}" = "alpha" || "${ENV}" = "staging" ]]; then
    echo "${ENV} not supported."
    exit 1
elif [ "${ENV}" = "local" ]; then
  ENV=dev
  WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/wsmtest/workspace/app-sa
else
  WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/${ENV}/${ENV}/workspace/app-sa
fi


WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/service-account.json
USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/${ENV}/common/firecloud-account.json
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
