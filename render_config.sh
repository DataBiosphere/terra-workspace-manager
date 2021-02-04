#!/bin/bash

# This script renders config related information in a file called "rendered".
# It assumes you have a vault token saved here: "$HOME"/.vault-token, but you can pass in
#   a vault token explicitly if you'd like
# Example: If you'd like to render configs for the dev environment you can run:
#    $ ./render_config.sh
#   or, more explicitly
#    $ ./render_config.sh dev

WSM_ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

if [ -z "${WSM_ENV}" ]; then
    echo "ENV not defined."
    exit 1
elif [[ "${WSM_ENV}" == "alpha" || "${WSM_ENV}" == "staging" ]]; then
    WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/${WSM_ENV}/${WSM_ENV}/workspace/app-sa
    USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/${WSM_ENV}/common/firecloud-account.json
elif [[ "${WSM_ENV}" == "dev" ]]; then
    WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/${WSM_ENV}/${WSM_ENV}/workspace/app-sa
    USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/integration/common/firecloud-account.json
else
    # All other envs are assumed to be within the 'integration' cluster.
    # Always use 'dev' for TestRunner user impersonation
    WM_APP_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/wsmtest/workspace/app-sa
    USER_DELEGATED_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/dev/common/firecloud-account.json
fi


WM_APP_SERVICE_ACCOUNT_OUTPUT_PATH=$(dirname "$0")/rendered/service-account.json
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
