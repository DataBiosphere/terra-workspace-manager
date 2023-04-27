#!/bin/bash
#
# Name: post-startup.sh
#
# Description
#   Default post startup script for Google Cloud Vertex AI Workbench VM
#   running JupyterLab.
#
# Execution details
#   The post-startup script runs on Vertex AI notebook VMs during *instance creation*;
#   it is not run on every instance start.
#
#   *** The post-startup script runs as root. ***
#
#   The startup script is executed from /opt/c2d/scripts/97-run-post-startup-script.sh
#   which will:
#     1- Get the GCS path from VM metadata (instance/attributes/post-startup-script)
#     2- Download it to /opt/c2d/post_start.sh
#     3- Execute /opt/c2d/post_start.sh
#     4- Set the VM guest attribute "notebooks/handle_post_startup_script" to "DONE"
#
#   Note that the guest attribute is set to DONE whether the script runs successfully or not.
#
# How to test changes to this file:
#   Copy this file to a GCS bucket:
#   - gsutil cp service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/gcp/ainotebook/post-startup.sh gs://MYBUCKET
#
#   Create a new VM:
#   - terra resource create gcp-notebook \
#       --name="test_post_startup" \
#       --post-startup-script=gs://MYBUCKET/post-startup.sh
#
#   To test a new command in this script, be sure to run with "sudo" in a JupyterLab Terminal.
#
# Integration Tests
#   Please also make sure integration test `PrivateControlledAiNotebookInstancePostStartup` passes. Refer to
#   https://github.com/DataBiosphere/terra-workspace-manager/tree/main/integration#Run-nightly-only-test-suite-locally
#   for instruction on how to run the test.
#

set -o errexit
set -o nounset
set -o pipefail
set -o xtrace

# The linux user that JupyterLab will be running as. It's important to do some parts of setup in the
# user space, such as setting Terra CLI settings which are persisted in the user's $HOME.
# This post startup script is not run by the same user.
readonly JUPYTER_USER="jupyter"
readonly STATUS_ATTRIBUTE="startup_script/status"
readonly MESSAGE_ATTRIBUTE="startup_script/message"
readonly USER_TERRA_CONFIG_DIR="/home/${JUPYTER_USER}/.terra"
readonly OUTPUT_FILE="${USER_TERRA_CONFIG_DIR}/post-startup-output.txt"

# Move to the /tmp directory to let any artifacts left behind by this script can be removed.
cd /tmp || exit

# Send stdout and stderr from this script to a file for debugging.
# Make the .terra directory as the user so that they own it and have correct linux permissions.
sudo -u "${JUPYTER_USER}" sh -c "mkdir -p ${USER_TERRA_CONFIG_DIR}"
exec >> "${OUTPUT_FILE}"
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
  local metadata_path="${1}"
  curl --retry 5 -s -f \
    -H "Metadata-Flavor: Google" \
    "http://metadata/computeMetadata/v1/${metadata_path}"
}

#######################################
# Set guest attributes on GCE. Used here to log completion status of the script.
# See https://cloud.google.com/compute/docs/metadata/manage-guest-attributes
# Arguments:
#   $1: The guest attribute domain and key IE startup_script/status
#   $2  The data to write to the guest attribute
#######################################
function set_guest_attributes() {
  local attr_path="${1}"
  local attr_value="${2}"
  curl -s -X PUT --data "${attr_value}" \
    -H "Metadata-Flavor: Google" \
    "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/${attr_path}"
}

# If the script exits without error let the UI know it completed successfully
# Otherwise if an error occurred write the line and command that failed to guest attributes.
function exit_handler {
  local exit_code="${1}"
  local line_no="${2}"
  local command="${3}"
  # Success! Set the guest attributes and exit cleanly
  if [[ "${exit_code}" -eq 0 ]]; then
    set_guest_attributes "${STATUS_ATTRIBUTE}" "COMPLETE"
    exit 0
  fi
  # Write error status and message to guest attributes
  set_guest_attributes "${STATUS_ATTRIBUTE}" "ERROR"
  set_guest_attributes "${MESSAGE_ATTRIBUTE}" "Error on line ${line_no}, command \"${command}\". See ${OUTPUT_FILE} for more information."
  exit "${exit_code}"
}
trap 'exit_handler $? $LINENO $BASH_COMMAND' EXIT

# Let the UI know the script has started
set_guest_attributes "${STATUS_ATTRIBUTE}" "STARTED"

# Install common packages. Use pip instead of conda because conda is slow.
/opt/conda/bin/pip install \
  dsub \
  nbdime \
  nbstripout \
  pandas_gbq \
  pre-commit \
  pylint \
  pytest

# Install nbstripout for the jupyter user in all git repositories.
sudo -u "${JUPYTER_USER}" sh -c "/opt/conda/bin/nbstripout --install --global"

# The apt package index may not be clean when we run; resynchronize
apt-get update

#########################################################
# Install required JDK and set it as default (debian)
#########################################################
function install_java() {
  curl -Os https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.deb
  apt-get install -y ./jdk-17_linux-x64_bin.deb
  update-alternatives --install /usr/bin/java java /usr/lib/jvm/jdk-17/bin/java 1
  update-alternatives --set java /usr/lib/jvm/jdk-17/bin/java
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

# Download Nextflow and install it
sudo -u "${JUPYTER_USER}" sh -c "curl -s https://get.nextflow.io | bash"
mv nextflow /usr/bin/nextflow

# Download Cromwell and install it
readonly CROMWELL_LATEST_VERSION="81"
curl -LO "https://github.com/broadinstitute/cromwell/releases/download/${CROMWELL_LATEST_VERSION}/cromwell-${CROMWELL_LATEST_VERSION}.jar"
mv "cromwell-${CROMWELL_LATEST_VERSION}.jar" "/usr/share/java/"

# Set a variable for the user in the bash_profile
cat << EOF >> "/home/${JUPYTER_USER}/.bash_profile"

# Set a convenience variable pointing to the version-specific Cromwell JAR file
export CROMWELL_JAR='/usr/share/java/cromwell-${CROMWELL_LATEST_VERSION}.jar'
EOF

# Download cromshell and install it
apt-get -y install mailutils
curl -Os https://raw.githubusercontent.com/broadinstitute/cromshell/master/cromshell
chmod +x cromshell
mv cromshell /usr/bin/cromshell

# Install & configure the Terra CLI
sudo -u "${JUPYTER_USER}" sh -c "curl -L https://github.com/DataBiosphere/terra-cli/releases/latest/download/download-install.sh | bash"
cp terra /usr/bin/terra
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
# Generate the bash completion scripot
sudo -u "${JUPYTER_USER}" sh -c "terra generate-completion" > /etc/bash_completion.d/terra

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

# GOOGLE_PROJECT is the project id for the GCP project backing the workspace
readonly GOOGLE_PROJECT="$(
  sudo -u "${JUPYTER_USER}" sh -c "terra workspace describe --format=json" | \
  jq --raw-output ".googleProjectId")"

# PET_SA_EMAIL is the pet service account for the Terra user and
# is specific to the GCP project backing the workspace
readonly PET_SA_EMAIL="$(
  sudo -u "${JUPYTER_USER}" sh -c "terra auth status --format=json" | \
  jq --raw-output ".serviceAccountEmail")"

# These are equivalent environment variables which are set for a
# command when calling "terra app execute <command>".
#
# TERRA_USER_EMAIL is the Terra user account email address.
# GOOGLE_CLOUD_PROJECT is the project id for the GCP project backing the
# workspace.
# GOOGLE_SERVICE_ACCOUNT_EMAIL is the pet service account for the Terra user
# and is specific to the GCP project backing the workspace.

cat << EOF >> "/home/${JUPYTER_USER}/.bash_profile"

# Set up a few legacy Terra-specific convenience variables
export OWNER_EMAIL='${OWNER_EMAIL}'
export GOOGLE_PROJECT='${GOOGLE_PROJECT}'
export PET_SA_EMAIL='${PET_SA_EMAIL}'

# Set up a few Terra-specific convenience variables
export TERRA_USER_EMAIL='${OWNER_EMAIL}'
export GOOGLE_CLOUD_PROJECT='${GOOGLE_PROJECT}'
export GOOGLE_SERVICE_ACCOUNT_EMAIL='${PET_SA_EMAIL}'
EOF

#################
# bash completion
#################
#
# bash_completion is installed on Vertex AI notebooks, but the installed
# completion scripts are *not* sourced from /etc/profile.
# If we need it system-wide, we can install it there, but otherwise, let's
# keep changes localized to the JUPYTER_USER.
#
cat << 'EOF' >> "/home/${JUPYTER_USER}/.bash_profile"

# Source available global bash tab completion scripts
if [[ -d /etc/bash_completion.d ]]; then
  for BASH_COMPLETION_SCRIPT in /etc/bash_completion.d/* ; do
    source "${BASH_COMPLETION_SCRIPT}"
  done
fi

# Source available user installed bash tab completion scripts
if [[ -d ~/bash_completion.d ]]; then
  for BASH_COMPLETION_SCRIPT in ~/bash_completion.d/* ; do
    source "${BASH_COMPLETION_SCRIPT}"
  done
fi
EOF

###############
# git setup
###############

sudo -u "${JUPYTER_USER}" sh -c "mkdir -p /home/${JUPYTER_USER}/.ssh"
cd "/home/${JUPYTER_USER}"
readonly TERRA_SSH_KEY="$(sudo -u "${JUPYTER_USER}" sh -c "terra user ssh-key get --include-private-key --format=JSON")"

# Set ssh-agent launch command in .bash_profile so everytime
# user starts a shell, we start the ssh-agent.
cat << 'EOF' >> "/home/${JUPYTER_USER}/.bash_profile"

# Start the ssh-agent
eval "$(ssh-agent -s)"
EOF

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

#############################
# Setup instance boot service
#############################
# Create a script to perform the following steps every time the instance boots:
# 1. Mount terra workspace resources. This command requires system user home
#    directories to be mounted. We run the startup service after
#    jupyter.service to meet this requirement.

# Create the boot script
readonly TERRA_BOOT_SCRIPT="${USER_TERRA_CONFIG_DIR}/instance-boot.sh"
readonly TERRA_BOOT_SERVICE="/etc/systemd/system/terra-instance-boot.service"

cat <<EOF >"${TERRA_BOOT_SCRIPT}"
#!/bin/bash

# Mount terra workspace resources
/usr/bin/terra resource mount
EOF
chmod +x "${TERRA_BOOT_SCRIPT}"

# Create a systemd service to run the boot script on system boot
cat <<EOF >"${TERRA_BOOT_SERVICE}"
[Unit]
Description=Configure environment for terra
After=jupyter.service

[Service]
ExecStart=${TERRA_BOOT_SCRIPT}
User=${JUPYTER_USER}
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

# Enable and start the startup service
sudo systemctl daemon-reload
sudo systemctl enable terra-setup-environment.service
sudo systemctl start terra-setup-environment.service

# Setup gitignore to avoid accidental checkin of data.
readonly GIT_IGNORE="/home/${JUPYTER_USER}/gitignore_global"

cat <<EOF | sudo --preserve-env -u "${JUPYTER_USER}" tee "${GIT_IGNORE}"
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

# Let the UI know the script completed
set_guest_attributes "${STATUS_ATTRIBUTE}" "COMPLETE"

####################################
# Restart kernel so environment variables work in notebook. See PF-2178.
####################################
systemctl restart jupyter.service
