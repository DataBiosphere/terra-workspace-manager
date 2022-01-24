#!/bin/bash
set -e

# Helper script to set up Skaffold for local development.
# This clones the terra-helmfile git repo, and templates in the desired
# Terra environment/k8s namespace to target.
# If you need to pull changes to terra-helmfile, rerun this script.

# Required input
ENV=$1
# Optional input
GIT_PROTOCOL=${2:-http}
TERRA_HELMFILE_BRANCH=${3:-master}

if [ "$GIT_PROTOCOL" = "http" ]; then
    helmfilegit=https://github.com/broadinstitute/terra-helmfile
else
    # use ssh
    helmfilegit=git@github.com:broadinstitute/terra-helmfile.git
fi

# Clone helmfile repo
rm -rf terra-helmfile

# Clone minimal state from repos and remove .git dir (nested .git directories confuse humans, IDE, and git)
git clone -b "$TERRA_HELMFILE_BRANCH" --single-branch --depth=1 ${helmfilegit}
rm -rf terra-helmfile/.git

# Render manifests to terra-helmfile/output/ directory.
#
# Unfortunately we need to render them all into a single mega-file
# because Skaffold's `kubectl` deployment does not support
# recursive globbing like "output/manifests/**/*.yaml"
mkdir -p ./terra-helmfile/output
./terra-helmfile/bin/render \
  -e "${ENV}" \
  -a workspacemanager \
  --stdout > terra-helmfile/output/manifests.yaml

# Template in environment
sed "s|ENV|${ENV}|g" skaffold.yaml.template > skaffold.yaml
sed "s|ENV|${ENV}|g" values.yaml.template > values.yaml

# That's it! You can now deploy to the k8s cluster by running
# $ skaffold run
# Or by using IntelliJ's Cloud Code integration, which will auto-detect the generated skaffold.yaml file.
