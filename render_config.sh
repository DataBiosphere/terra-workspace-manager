#!/bin/bash

VAULT_TOKEN=${1:-$(cat $HOME/.vault-token)}
TARGET_ENV=${2:-dev}

FIRECLOUD_ACCOUNT_VAULT_PATH=secret/dsde/firecloud/${TARGET_ENV}/common/firecloud-account.json
SERVICE_ACCOUNT_OUTPUT_FILE_PATH="$(dirname $0)"/src/test/resources/rendered/wsm-firecloud-account.json

if [[ ! "$TARGET_ENV" =~ ^(dev|alpha|perf|staging|prod)$ ]]; then
    printf "\033[0;31m Unknown environment: $TARGET_ENV \n Must be one of [dev, alpha, perf, staging, prod] \n\033[0m"
    exit 1
fi

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

if type vault > /dev/null; then
  SERVICE_ACCOUNT_CREDS=$(VAULT_ADDR="https://clotho.broadinstitute.org:8200" VAULT_TOKEN=$VAULT_TOKEN vault read -format=json ${FIRECLOUD_ACCOUNT_VAULT_PATH} | jq .data)
else
  SERVICE_ACCOUNT_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN ${DSDE_TOOLBOX_DOCKER_IMAGE} \
      vault read -format=json ${FIRECLOUD_ACCOUNT_VAULT_PATH} | \
      jq .data`
fi

if [[ -z "$SERVICE_ACCOUNT_CREDS" ]]; then
    printf "\033[0;31m Could not fetch service account creds. Check your vault token. \n\033[0m"
    exit 1
fi

echo "$SERVICE_ACCOUNT_CREDS" > ${SERVICE_ACCOUNT_OUTPUT_FILE_PATH}
