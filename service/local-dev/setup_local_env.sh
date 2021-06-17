#!/bin/bash
set -e

# Helper script to set up Skaffold for local development.
# This clones the terra-helm and terra-helmfile git repos, and templates in the desired
#   Terra environment/k8s namespace to target.
# If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

# Required input
ENV=$1
# Optional input
TERRA_HELMFILE_BRANCH=${2:-master}
GIT_PROTOCOL=${3:-http}

if [ "$GIT_PROTOCOL" = "http" ]; then
    helmfilegit=https://github.com/broadinstitute/terra-helmfile
else
    helmfilegit=git@github.com:broadinstitute/terra-helmfile.git
fi

# Clone Helm chart and helmfile repos
rm -rf terra-helmfile
git clone -b "$TERRA_HELMFILE_BRANCH" --single-branch ${helmfilegit}

# Render manifests to manifests.yaml
./terra-helmfile/bin/render \
  -e "${ENV}" \
  -a workspacemanager \
  --stdout > \
  manifests.yaml

# Template in environment
sed "s|ENV|${ENV}|g" skaffold.yaml.template > skaffold.yaml

# That's it! You can now deploy to the k8s cluster by running
# $ skaffold run
# Or by using IntelliJ's Cloud Code integration, which will auto-detect the generated skaffold.yaml file.
