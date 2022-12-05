#!/bin/bash

set -e

# Create the google cloud yum repo config
tee -a /etc/yum.repos.d/google-cloud-sdk.repo << EOM
[google-cloud-cli]
name=Google Cloud CLI
baseurl=https://packages.cloud.google.com/yum/repos/cloud-sdk-el8-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=0
gpgkey=https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOM

# Install gcloud CLI
yum install -y google-cloud-cli

# In bash login script, check if the environment has been configured for Terra and, if not, attempt to do so.
cat << EOM | sed -i '/^# User specific aliases and functions$/ r /dev/stdin' /home/ec2-user/.bashrc
if [ ! -f '/home/ec2-user/SageMaker/.terra/notebook_metadata.json' ]; then
    # The user has not configured their environment to access Terra yet, call now.
    /home/ec2-user/terra/terra-auth.py --configure
    retVal=\$?

    # If this got status 1, that means user was not authenticated.  Prompt them to authenticate
    # and try again.
    if [ \${retVal} -eq 1 ]; then
      echo "Please log in with Google Application Default Credentials in order to authenticate with Terra.\n"
      /usr/bin/gcloud auth application-default login
      /home/ec2-user/terra/terra-auth.py --configure
    fi
fi
EOM

# Change the terminal shell to bash
echo "c.NotebookApp.terminado_settings={'shell_command': ['/bin/bash']}" | tee -a /home/ec2-user/.jupyter/jupyter_notebook_config.py >/dev/null

sudo -u ec2-user -i <<'EOF'

# Install google auth python package in all conda environments

# Note that "base" is special environment name, include it there as well.
for env in base /home/ec2-user/anaconda3/envs/*; do
    source /home/ec2-user/anaconda3/bin/activate $(basename "$env")
    if [ $env = 'JupyterSystemEnv' ]; then
        continue
    fi
    pip install google-auth
    source /home/ec2-user/anaconda3/bin/deactivate
done

# Copy in the Terra auth helper CLI (eventually this will install Terra CLI instead)
WORKING_DIR=/home/ec2-user/terra
mkdir -p "$WORKING_DIR"
wget https://raw.githubusercontent.com/DataBiosphere/terra-workspace-manager/jczerk/aws_wlz_interface/service/src/main/resources/tools/terra-auth.py -O "$WORKING_DIR/terra-auth.py"
chmod +x "$WORKING_DIR/terra-auth.py"

# Lazy create terra persistence directory and symlink
mkdir -p /home/ec2-user/SageMaker/.terra
ln -s /home/ec2-user/SageMaker/.terra /home/ec2-user/.terra

# Lazy create gcloud persistence directory and symlink
mkdir -p /home/ec2-user/SageMaker/.config/gcloud
mkdir -p /home/ec2-user/.config
ln -s /home/ec2-user/SageMaker/.config/gcloud /home/ec2-user/.config/gcloud

# If we have ADC, attempt to re-configure at startup.
if [ -f /home/ec2-user/.config/gcloud/application_default_credentials.json ]; then
  /home/ec2-user/terra/terra-auth.py --configure
fi

EOF
