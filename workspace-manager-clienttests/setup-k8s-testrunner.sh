#!/bin/bash
set -e

# This script sets up the necessary Kubernetes objects and grant them the
# necessary permissions to run resiliency tests in a nameapce. These objects are:
#
# Test Runner K8S Service Account
# Test Runner RBAC Role
# Test Runner RBAC RoleBinding

# USAGE: ./setup-k8s-testrunner.sh <kubeconfig-context-name> wsmtest workspacemanager
# WARNING: Please make sure the Kubernetes context and namespace arguments are the intended k8s environment to create the Test Runner Service Account.

# After running this script with proper input arguments, you can run the following command
# to render the Test Runner K8S credentials to run resiliency tests in the namespace:
#
# ./render-k8s-config.sh <NAMESPACE>
#
# Example: ./render-k8s-config.sh wsmtest

# Required input
KUBECONTEXT=$1
if [ -z "$1" ]
  then
    echo "Please specify the ~/.kube/config context that defines the cluster where the Kubernetes objects will be created."
    echo "Your application default credentials must have priviledged access to the cluster."
    echo "Usage: ./setup-k8s-testrunner.sh <kubeconfig-context-name> wsmtest workspacemanager"
    exit 1;
fi
kubectl config use-context "${KUBECONTEXT}"
NAMESPACE=$2
if [ -z "$2" ]
  then
    echo "Please specify a valid namespace (without the terra- prefix) as the second argument (e.g. wsmtest)."
    echo "Usage: ./setup-k8s-testrunner.sh <kubeconfig-context-name> wsmtest workspacemanager"
    exit 1;
fi
APP=$3
if [ -z "$3" ]
  then
    echo "Please provide a component label as the third argument."
    echo "This typically is a short string that describes the application."
    echo "Usage: ./setup-k8s-testrunner.sh wsmtest workspacemanager"
    exit 1;
fi
VAULT_TOKEN=${4:-$(cat "$HOME"/.vault-token)}

# Template in NAMESPACE and APP
cat testrunner-k8s-serviceaccount.yml.template | \
    sed "s|NAMESPACE|${NAMESPACE}|g" | \
    sed "s|APP|${APP}|g" > testrunner-k8s-serviceaccount.yml

cat testrunner-k8s-role.yml.template | \
    sed "s|NAMESPACE|${NAMESPACE}|g" | \
    sed "s|APP|${APP}|g" > testrunner-k8s-role.yml

cat testrunner-k8s-rolebinding.yml.template | \
    sed "s|NAMESPACE|${NAMESPACE}|g" | \
    sed "s|APP|${APP}|g" > testrunner-k8s-rolebinding.yml

echo "Provisioning the necessary Kubernetes objects in namespace terra-${NAMESPACE}."
kubectl apply -f testrunner-k8s-serviceaccount.yml -n "terra-${NAMESPACE}"
kubectl apply -f testrunner-k8s-role.yml -n "terra-${NAMESPACE}"
kubectl apply -f testrunner-k8s-rolebinding.yml -n "terra-${NAMESPACE}"

echo "Store Test Runner K8S Secrets in Vault."
SECRET_NAME=$(kubectl get sa "testrunner-k8s-sa" -n "terra-${NAMESPACE}" -ojson | jq ".secrets[0].name" -r)
kubectl get secret "${SECRET_NAME}" -n "terra-${NAMESPACE}" -ojson | base64 > "${HOME}/testrunner-k8s-sa.crt"

DSDE_TOOLBOX_DOCKER_IMAGE=broadinstitute/dsde-toolbox:consul-0.20.0

TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH=secret/dsde/terra/kernel/integration/${NAMESPACE}/testrunner-k8s-sa

docker run --rm -v "${HOME}:/working" -e VAULT_TOKEN="${VAULT_TOKEN}" \
    $DSDE_TOOLBOX_DOCKER_IMAGE \
    vault write "${TESTRUNNER_K8S_SERVICE_ACCOUNT_VAULT_PATH}" key=@testrunner-k8s-sa.crt

# Clean up generated files
rm testrunner-k8s-serviceaccount.yml
rm testrunner-k8s-role.yml
rm testrunner-k8s-rolebinding.yml
rm "${HOME}/testrunner-k8s-sa.crt"
