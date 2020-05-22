#!/bin/bash

VAULT_TOKEN=${1:-$(cat $HOME/.vault-token)}
TARGET_ENV=${2:-dev}

DOCKER_MOUNT_DIRECTORY=terra-workspace-manager
TEMPLATE_INPUT_PATH=${DOCKER_MOUNT_DIRECTORY}/config-templates
TEMPLATE_OUTPUT_PATH=${DOCKER_MOUNT_DIRECTORY}/src/test/resources
FIRECLOUD_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/${TARGET_ENV}/common/firecloud-account.json
SERVICE_ACCOUNT_OUTPUT_FILE_PATH=/tmp/wsm-firecloud-account.json

if [[ ! "$TARGET_ENV" =~ ^(dev|alpha|perf|staging|prod)$ ]]; then
    printf "\033[0;31m Unknown environment: $TARGET_ENV \n Must be one of [dev, alpha, perf, staging, prod] \n\033[0m"
    exit 1
fi

SERVICE_ACCOUNT_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN broadinstitute/dsde-toolbox:latest \
    vault read -format=json ${FIRECLOUD_ACCOUNT_VAULT_PATH} | \
    jq .data`

if [[ -z "$SERVICE_ACCOUNT_CREDS" ]]; then
    printf "\033[0;31m Could not fetch service account creds. Check your vault token. \n\033[0m"
    exit 1
fi

echo "$SERVICE_ACCOUNT_CREDS" > ${SERVICE_ACCOUNT_OUTPUT_FILE_PATH}

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

# render configs
docker run -v $PWD:/${DOCKER_MOUNT_DIRECTORY} \
  -e RUN_CONTEXT=live \
  -e INPUT_PATH=/${TEMPLATE_INPUT_PATH} \
  -e OUT_PATH=/${TEMPLATE_OUTPUT_PATH} \
  -e VAULT_TOKEN=${VAULT_TOKEN} \
  -e ENVIRONMENT=${TARGET_ENV} \
  -e SERVICE_ACCOUNT_FILE_PATH=${SERVICE_ACCOUNT_OUTPUT_FILE_PATH} \
   $DSDE_TOOLBOX_DOCKER_IMAGE render-templates.sh