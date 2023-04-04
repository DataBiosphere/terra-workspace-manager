#!/bin/bash
# This script assumes it is being run from the top-level terra-service-ws-manager dir.
#
# Render configuration necessary to run and test with a local WSM instance
# backed by services in Verily's devel environment. Unlike the Broad's deployment
# we do not expose RBS in other environments for testing, so all local and
# connected tests must run in the devel environment.
#
# This script will use your existing gcloud credentials.

# Usage: `./scripts/write-verily-config.sh [<use_manual_sa>] [<use_local_app>]`
#
# use_manual_sa is an optional argument stating whether this script should fetch
# and store the credentials for the "wsm_manual_testuser_sa" service account.
# The default value is true. If this is false, this script will not fetch the
# SA credentials or store them in  config/testuser-sa-key.json.
#
# use_local_app is an optional argument stating whether this script should render
# a WSM application used for local integration testing. The default value is true.
use_manual_sa=${1:-true}
use_local_app=${2:-true}
config_dir="./config"

# Checkout latest clean version of the submodule
git submodule foreach --recursive git clean -xfd
git submodule foreach --recursive git reset --hard
git submodule update --init --recursive

# Create the config directory, which won't exist after the cleaning above.
mkdir -p "${config_dir}"

# Read Buffer client SA key from GCP and store it in WSM's config dir
gcloud secrets versions access "latest" --secret="terra-buffer-client-sa-key" --project=terra-devel > "${config_dir}/buffer-client-sa.json"

# Read devel WSM SA key from GCP and store it in WSM's config dir
gcloud secrets versions access "latest" --secret="terra-ws-manager-sa" --project=terra-devel > "${config_dir}/wsm-sa.json"

# Read devel TPS SA key from GCP and store it in WSM's config dir
gcloud secrets versions access "latest" --secret="terra-policy-client-sa-key" --project=terra-devel > "${config_dir}/policy-client-sa.json"

# If using the manual testing SA, fetch and store it
if [ "$use_manual_sa" = true ]; then
  gcloud secrets versions access "latest" --secret="wsm_manual_testuser_sa" --project=terra-devel > "${config_dir}/testuser-sa-key.json"
fi
