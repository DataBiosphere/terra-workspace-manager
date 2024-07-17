#!/bin/bash
set -euo pipefail

# Builds the current WSM branch and pushes container images
# for AzureDatabaseUtils and WSM to GCR. Useful for testing
# on BEEs.

SHA=$(git rev-parse HEAD)
./gradlew :service:jibDockerBuild --image="gcr.io/broad-dsp-gcr-public/terra-workspace-manager:$SHA"
docker push "gcr.io/broad-dsp-gcr-public/terra-workspace-manager:$SHA"
./gradlew :azureDatabaseUtils:jibDockerBuild --image="us.gcr.io/broad-dsp-gcr-public/azure-database-utils:$SHA"
docker push "us.gcr.io/broad-dsp-gcr-public/azure-database-utils:$SHA"

echo "App rev pushed to GCR $SHA"
