#!/bin/bash
#
# Default post startup script for GCP notebooks.
# GCP Notebook post startup scrips are run only when the instance is first created.
#
# How to test changes to this file:
# - gsutil cp service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/gcp/ainotebook/post-startup.sh gs://MYBUCKET
# - terra resource create gcp-notebook --post-startup-script=gs://MYBUCKET/post-startup.sh --name="test_post_startup"
#
# To test a single line, run with "sudo" in notebook. Post-startup script runs
# as root.
#
# Please also make sure integration test `PrivateControlledAiNotebookInstancePostStartup` passes. Refer to
# https://github.com/DataBiosphere/terra-workspace-manager/tree/main/integration#Run-nightly-only-test-suite-locally
# for instruction on how to run the test.

set -o errexit
set -o nounset
set -o pipefail
set -o xtrace

# The linux user that JupyterLab will be running as. It's important to do some parts of setup in the
# user space, such as setting Terra CLI settings which are persisted in the user's $HOME.
# This post startup script is not run by the same user.
readonly JUPYTER_USER="jupyter"

# Move to the /tmp directory to let any artifacts left behind by this script can be removed.
cd /tmp || exit

# Send stdout and stderr from this script to a file for debugging.
# Make the .terra directory as the user so that they own it and have correct linux permissions.
sudo -u "${JUPYTER_USER}" sh -c "mkdir -p /home/${JUPYTER_USER}/.terra"
exec >> /home/"${JUPYTER_USER}"/.terra/post-startup-output.txt
exec 2>&1

#######################################
# Retrieve a value from the GCE metadata server or return nothing.
# See https://cloud.google.com/compute/docs/storing-retrieving-metadata
# Arguments:
#   The metadata subpath to retrieve
# Returns:
#   The metadata value if found, or else an empty string
#######################################
function get_metadata_value() {
  curl --retry 5 -s -f \
    -H "Metadata-Flavor: Google" \
    "http://metadata/computeMetadata/v1/$1"
}

# Install common packages. Use pip instead of conda because conda is slow.
/opt/conda/bin/pip install pre-commit nbdime nbstripout pylint pytest dsub pandas_gbq

# Install nbstripout for the jupyter user in all git repositories.
sudo -u "${JUPYTER_USER}" sh -c "/opt/conda/bin/nbstripout --install --global"

# Install Nextflow. Use an edge release that allows overriding the default compute engine SA and VPC network
export NXF_VER=21.05.0-edge
export NXF_MODE=google

sudo apt-get update

#########################################################
# Install required JDK and set it as default (debian)
#########################################################
function install_java() {
  curl -Os https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.deb
  sudo apt-get install -y ./jdk-17_linux-x64_bin.deb
  sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/jdk-17/bin/java 1
  sudo update-alternatives --set java /usr/lib/jvm/jdk-17/bin/java
}

if [[ -n "$(which java)" ]];
then
  # Get the current major version of Java: "11.0.12" => "11"
  readonly CUR_JAVA_VERSION="$(java -version 2>&1 | awk -F\" '{ split($2,a,"."); print a[1]}')"
  if [[ "${CUR_JAVA_VERSION}" -lt 17 ]];
  then
    echo "Current Java version is ${CUR_JAVA_VERSION}, installing Java 17"
    install_java
  else
    echo "Java 17 is installed"
  fi
else
  echo "Installing Java 17"
  install_java
fi

sudo -u "${JUPYTER_USER}" sh -c "curl -s https://get.nextflow.io | bash"
sudo mv nextflow /usr/bin/nextflow

# Install cromwell
readonly CROMWELL_LATEST_VERSION="81"
sudo -u "${JUPYTER_USER}" sh -c "mkdir -p /home/${JUPYTER_USER}/cromwell"
sudo -u "${JUPYTER_USER}" sh -c "curl -LO https://github.com/broadinstitute/cromwell/releases/download/${CROMWELL_LATEST_VERSION}/cromwell-${CROMWELL_LATEST_VERSION}.jar"
mv "cromwell-${CROMWELL_LATEST_VERSION}.jar" "/home/${JUPYTER_USER}/cromwell/"

#Install cromshell
sudo apt-get -y install mailutils
sudo -u "${JUPYTER_USER}" sh -c "curl -s https://raw.githubusercontent.com/broadinstitute/cromshell/master/cromshell > cromshell"
sudo -u "${JUPYTER_USER}" sh -c "chmod +x cromshell"
sudo mv cromshell /usr/bin/cromshell

# Install & configure the Terra CLI
sudo -u "${JUPYTER_USER}" sh -c "curl -L https://github.com/DataBiosphere/terra-cli/releases/latest/download/download-install.sh | bash"
sudo cp terra /usr/bin/terra
# Set browser manual login since that's the only login supported from an GCP Notebook VM.
sudo -u "${JUPYTER_USER}" sh -c "terra config set browser MANUAL"
# Set the CLI terra server based on the terra server that created the GCP notebook retrieved from
# the VM metadata, if set.
readonly TERRA_SERVER="$(get_metadata_value "instance/attributes/terra-cli-server")"
if [[ -n "${TERRA_SERVER}" ]]; then
  sudo -u "${JUPYTER_USER}" sh -c "terra server set --name=${TERRA_SERVER}"
fi

# Log in with app-default-credentials
sudo -u "${JUPYTER_USER}" sh -c "terra auth login --mode=APP_DEFAULT_CREDENTIALS"

####################################
# Shell and notebook environment
####################################

# Set the CLI terra workspace id using the VM metadata, if set.
readonly TERRA_WORKSPACE="$(get_metadata_value "instance/attributes/terra-workspace-id")"
if [[ -n "${TERRA_WORKSPACE}" ]]; then
  sudo -u "${JUPYTER_USER}" sh -c "terra workspace set --id=${TERRA_WORKSPACE}"
fi

# Set variables into the .bash_profile such that they are available
# to terminals, notebooks, and other tools
#
# We have new-style variables (eg GOOGLE_CLOUD_PROJECT) which are set here
# and CLI (terra app execute env).
# We also support a few variables set by Leonardo (eg GOOGLE_PROJECT).
# Those are only set here and NOT in the CLI as they are intended just
# to make porting existing notebooks easier.

# Keep in sync with terra CLI environment variables:
# https://github.com/DataBiosphere/terra-cli/blob/14cf51dd809573c7ae9a3ef10ddd427fa057cb8f/src/main/java/bio/terra/cli/app/CommandRunner.java#L88

# *** Variables that are set by Leonardo for Cloud Environments
# (https://github.com/DataBiosphere/leonardo)

# OWNER_EMAIL is really the Terra user account email address

readonly OWNER_EMAIL="$(
  sudo -u "${JUPYTER_USER}" sh -c "terra workspace describe --format=json" | \
  jq --raw-output ".userEmail")"
echo "export OWNER_EMAIL='${OWNER_EMAIL}'" >> "/home/${JUPYTER_USER}/.bash_profile"

# GOOGLE_PROJECT is the project id for the GCP project backing the workspace

readonly GOOGLE_PROJECT="$(
  sudo -u "${JUPYTER_USER}" sh -c "terra workspace describe --format=json" | \
  jq --raw-output ".googleProjectId")"
echo "export GOOGLE_PROJECT='${GOOGLE_PROJECT}'" >> "/home/${JUPYTER_USER}/.bash_profile"

# PET_SA_EMAIL is the pet service account for the Terra user and
# is specific to the GCP project backing the workspace

readonly PET_SA_EMAIL="$(
  sudo -u "${JUPYTER_USER}" sh -c "terra auth status --format=json" | \
  jq --raw-output ".serviceAccountEmail")"
echo "export PET_SA_EMAIL='${PET_SA_EMAIL}'" >> "/home/${JUPYTER_USER}/.bash_profile"

# These are equivalent environment variables which are set for a
# command when calling "terra app execute <command>".

# TERRA_USER_EMAIL is the Terra user account email address.

echo "export TERRA_USER_EMAIL='${OWNER_EMAIL}'" >> "/home/${JUPYTER_USER}/.bash_profile"

# GOOGLE_CLOUD_PROJECT is the project id for the GCP project backing the
# workspace.

echo "export GOOGLE_CLOUD_PROJECT='${GOOGLE_PROJECT}'" >> "/home/${JUPYTER_USER}/.bash_profile"

# GOOGLE_SERVICE_ACCOUNT_EMAIL is the pet service account for the Terra user
# and is specific to the GCP project backing the workspace.

echo "export GOOGLE_SERVICE_ACCOUNT_EMAIL='${PET_SA_EMAIL}'" >> "/home/${JUPYTER_USER}/.bash_profile"

###############
# git setup
###############

sudo -u "${JUPYTER_USER}" sh -c "mkdir -p /home/${JUPYTER_USER}/.ssh"
cd "/home/${JUPYTER_USER}"
readonly TERRA_SSH_KEY="$(sudo -u "${JUPYTER_USER}" sh -c "terra user ssh-key get --format=JSON")"

# Start the ssh-agent. Set this command in bash_profile so everytime user starts a shell, we start the ssh-agent.
echo eval '"$(ssh-agent -s)"' >> .bash_profile
if [[ -n "$TERRA_SSH_KEY" ]]; then
  printf '%s' "$TERRA_SSH_KEY" | sudo -u "${JUPYTER_USER}" sh -c "jq -r '.privateSshKey' > .ssh/id_rsa"
  sudo -u "${JUPYTER_USER}" sh -c 'chmod go-rwx .ssh/id_rsa'
  sudo -u "${JUPYTER_USER}" sh -c 'ssh-add .ssh/id_rsa; ssh-keyscan -H github.com >> ~/.ssh/known_hosts'
fi

# Attempt to clone all the git repo references in the workspace. If the user's ssh key does not exist or doesn't have access
# to the git references, the corresponding git repo cloning will be skipped.
# Keep this as last thing in script. There will be integration test for git cloning (PF-1660). If this is last thing, then
# integration test will ensure that everything in script worked.
sudo -u "$JUPYTER_USER" sh -c 'terra git clone --all'

# Setup gitignore to avoid accidental checkin of data. 
readonly GIT_IGNORE="/home/${JUPYTER_USER}/gitignore_global"

cat <<EOF | sudo -E -u jupyter tee "${GIT_IGNORE}"
# By default, all files should be ignored by git.
# We want to be sure to exclude files containing data such as CSVs and images such as PNGs.
*.*
# Now, allow the file types that we do want to track via source control.
!*.ipynb
!*.py
!*.r
!*.R
!*.wdl
!*.sh
# Allow documentation files.
!*.md
!*.rst
!LICENSE*
EOF

sudo -u "$JUPYTER_USER" sh -c "git config --global core.excludesfile ${GIT_IGNORE}"

# This block is for test only. If the notebook execute successfully down to
# here, we knows that the script executed successfully.
readonly TERRA_TEST_VALUE="$(get_metadata_value "instance/attributes/terra-test-value")"
readonly TERRA_GCP_NOTEBOOK_RESOURCE_NAME="$(get_metadata_value "instance/attributes/terra-gcp-notebook-resource-name")"
if [[ -n "${TERRA_TEST_VALUE}" ]]; then
  sudo -u "${JUPYTER_USER}" sh -c "terra resource update gcp-notebook --name=${TERRA_GCP_NOTEBOOK_RESOURCE_NAME} --new-metadata=terra-test-result=${TERRA_TEST_VALUE}"
fi
