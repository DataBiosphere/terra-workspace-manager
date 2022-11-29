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
sudo yum install -q -y google-cloud-cli

# Install google auth python package
pip install --upgrade google-auth

# Copy in the Terra auth helper CLI (eventually this will install Terra CLI instead)
WORKING_DIR=/home/ec2-user/terra
mkdir -p "$WORKING_DIR"
wget https://raw.githubusercontent.com/DataBiosphere/terra-workspace-manager/jczerk/aws_wlz_interface/service/src/main/resources/tools/terra-auth.py -O "$WORKING_DIR/terra-auth.py"
chmod +x "$WORKING_DIR/terra-auth.py"
