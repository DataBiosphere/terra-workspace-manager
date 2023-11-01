#!/bin/bash
#
# write-config.sh extracts configuration information from vault and writes it to a set of files
# in a directory. This simplifies access to the secrets from other scripts and applications.
#
# This is intended as a replacement for render_config.sh in the service project and the
# render-config.sh in the integration project. We want to avoid writing into the source
# tree. Instead, this will write into a subdirectory of the root directory.
#
# We want to use this in a gradle task, so it takes arguments both as command line
# options and as envvars. For automations, like GHA, the command line can be specified.
# For developer use, we can set our favorite envvars and let gradle ensure the directory is properly populated.
#
# The environment passed in is used to configure several other parameters, including the target for running
# the integration tests.
#
# For personal environments, we assume that the target name is the same as the personal namespace name.
# The output directory includes the following files:
#   ---------------------------+-------------------------------------------------------------------------
#   buffer-client-sa.json      | Creds to access Buffer service
#   ---------------------------+-------------------------------------------------------------------------
#   policy-client-sa.json      | Creds to access Policy service
#   ---------------------------+-------------------------------------------------------------------------
#   db-connection-name.txt     | Connection string for CloudSQL proxy
#   ---------------------------+-------------------------------------------------------------------------
#   db-name.txt                | Database name
#   ---------------------------+-------------------------------------------------------------------------
#   db-password.txt            | Database password
#   ---------------------------+-------------------------------------------------------------------------
#   db-username.txt            | Database username
#   ---------------------------+-------------------------------------------------------------------------
#   janitor-client-sa.json     | Creds to access Janitor service. These are only available in the
#                              | integration environment and are not retrieved in other environments.
#   ---------------------------+-------------------------------------------------------------------------
#   local-properties.yml       | Additional property file optionally loaded by application.yml. It is
#                              | populated with the test application configuration when the target is
#                              | "local". Otherwise, it is empty.
#   ---------------------------+-------------------------------------------------------------------------
#   sqlproxy-sa.json           | SA of the CloudSQL proxy
#   ---------------------------+-------------------------------------------------------------------------
#   stairway-db-name.txt       | Stairway database name
#   ---------------------------+-------------------------------------------------------------------------
#   stairway-db-password.txt   | Stairway database password
#   ---------------------------+-------------------------------------------------------------------------
#   stairway-db-username.txt   | Stairway database username
#   ---------------------------+-------------------------------------------------------------------------
#   target.txt                 | the target that generated this set of config files. Allows the script
#                              | to skip regenerating the environment on a rerun.
#   ---------------------------+-------------------------------------------------------------------------
#   testrunner-sa.json         | SA for running TestRunner - this is always taken from integration/common
#   ---------------------------+-------------------------------------------------------------------------
#   testrunner-k8s-sa-token.txt| Credentials for TestRunner to manipulate the Kubernetes cluster under
#   testrunner-k8s-sa-key.txt  | test. Not all environments have this SA configured. If the k8env is
#                              | integration and there is no configured SA, then the wsmtest one will be
#                              | retrieved. It won't work for all test runner tests.
#   ---------------------------+-------------------------------------------------------------------------
#   user-delegated-sa.json     | Firecloud SA used to masquerade as test users
#   ---------------------------+-------------------------------------------------------------------------
#   wsm-sa.json                | SA that the WSM application runs as
#   ---------------------------+-------------------------------------------------------------------------

function usage {
  cat <<EOF
Usage: $0 [<target>] [<vaulttoken>] [<outputdir>] [<vaultenv>]"

  <target> can be:
    local - for testing against a local (bootRun) WSM
    dev - uses secrets from the dev environment
    alpha - alpha test environment
    staging - release staging environment
    help or ? - print this help
    clean - removes all files from the output directory
    * - anything else is assumed to be a personal environment using the terra-kernel-k8s
  If <target> is not specified, then use the envvar WSM_WRITE_CONFIG
  If WSM_WRITE_CONFIG is not specified, then use local

  <vaulttoken> defaults to the token found in ~/.vault-token.

  <outputdir> defaults to "../config/" relative to the script. When run from the gradle rootdir, it will be
  in the expected place for automation.

  <vaultenv> can be:
    docker - run a docker image containing vault
    local  - run the vault installed locally
  If <vaultenv> is not specified, then use the envvar WSM_VAULT_ENV
  If WSM_VAULT_ENV is not specified, then we use docker
EOF
 exit 1
}

# Get the inputs with defaulting
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null  && pwd )"
default_outputdir="${script_dir}/config"
default_target=${WSM_WRITE_CONFIG:-local}
target=${1:-$default_target}
vaulttoken=${2:-$(cat "$HOME"/.vault-token)}
outputdir=${3:-$default_outputdir}
default_vaultenv=${WSM_VAULT_ENV:-docker}
vaultenv=${4:-$default_vaultenv}

# The vault paths are irregular, so we map the target into three variables:
# k8senv    - the kubernetes environment: alpha, staging, dev, or integration
# namespace - the namespace in the k8s env: alpha, staging, dev, or the target for personal environments
# fcenv     - the firecloud delegated service account environment: dev, alpha, staging

case $target in
    help | ?)
        usage
        ;;

    clean)
        rm "${outputdir}"/* &> /dev/null
        exit 0
        ;;

    local)
        k8senv=integration
        namespace=wsmtest
        fcenv=dev
        ;;

    dev)
        k8senv=dev
        namespace=dev
        fcenv=dev
        ;;

    alpha)
        k8senv=alpha
        namespace=alpha
        fcenv=alpha
        ;;

    staging)
        k8senv=staging
        namespace=staging
        fcenv=staging
        ;;


    *) # personal env
        k8senv=integration
        namespace=$target
        fcenv=dev
        ;;
esac

# Create the output directory if it doesn't already exist
mkdir -p "${outputdir}"

# If there is a config and it matches, don't regenerate
if [ -e "${outputdir}/target.txt" ]; then
    oldtarget=$(<"${outputdir}/target.txt")
    if [ "$oldtarget" = "$target" ]; then
        echo "Config for $target already written"
        exit 0
    fi
fi

# Run vault either using a docker container or using the installed vault
function dovault {
    local dovaultpath=$1
    local dofilename=$2
    case $vaultenv in
        docker)
            docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:dev \
                   vault read -format=json "${dovaultpath}" > "${dofilename}"
            ;;

        local)
            VAULT_TOKEN="${vaulttoken}" VAULT_ADDR="https://clotho.broadinstitute.org:8200" \
                   vault read -format=json "${dovaultpath}" > "${dofilename}"
            ;;
    esac
}

# Read a vault path into an output file, decoding from base64
# To detect missing tokens, we need to capture the docker result before
# doing the rest of the pipeline.
function vaultgetb64 {
    vaultpath=$1
    filename=$2
    fntmpfile=$(mktemp)
    dovault "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r .data.key "${fntmpfile}" | base64 -d > "${filename}"
}

# Read a vault path into an output file
function vaultget {
    vaultpath=$1
    filename=$2
    fntmpfile=$(mktemp)
    dovault "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r .data "${fntmpfile}" > "${filename}"
}

# Read database data from a vault path into a set of files
function vaultgetdb {
    vaultpath=$1
    fileprefix=$2
    fntmpfile=$(mktemp)
    dovault "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    datafile=$(mktemp)
    jq -r .data "${fntmpfile}" > "${datafile}"
    jq -r '.db' "${datafile}" > "${outputdir}/${fileprefix}-name.txt"
    jq -r '.password' "${datafile}" > "${outputdir}/${fileprefix}-password.txt"
    jq -r '.username' "${datafile}" > "${outputdir}/${fileprefix}-username.txt"
}

vaultget "secret/dsde/firecloud/${fcenv}/common/firecloud-account.json" "${outputdir}/user-delegated-sa.json"
vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/app-sa" "${outputdir}/wsm-sa.json"

# Janitor SA is only available in integration
if [ "${k8senv}" = "integration" ]; then
    vaultgetb64 "secret/dsde/terra/kernel/integration/tools/crl_janitor/client-sa" "${outputdir}/janitor-client-sa.json"
else
    echo "No janitor credentials for target ${target}"
fi

# Buffer SA naming is irregular
if [ "${k8senv}" = "integration" ]; then
    vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/tools/buffer/client-sa" "${outputdir}/buffer-client-sa.json"
else
    vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/buffer/client-sa" "${outputdir}/buffer-client-sa.json"
fi

# Policy SA is only setup for dev at this time
if [ "${k8senv}" = "integration" ]; then
    vaultgetb64 "secret/dsde/terra/kernel/dev/dev/tps/client-sa" "${outputdir}/policy-client-sa.json"
elif [ "${k8senv}" = "dev" ]; then
    vaultgetb64 "secret/dsde/terra/kernel/dev/dev/tps/client-sa" "${outputdir}/policy-client-sa.json"
else
    echo "No policy credentials for target ${target}"
fi

# Test Runner SA
vaultgetb64 "secret/dsde/terra/kernel/integration/common/testrunner/testrunner-sa" "${outputdir}/testrunner-sa.json"

# Test Runner Kubernetes SA
#
# The testrunner K8s secret has a complex structure. At secret/.../testrunner-k8s-sa we have the usual base64 encoded object
# under data.key. When that is pulled out and decoded we get a structure with:
# { "data":  { "ca.crt": <base64-cert>, "token": <base64-token> } }
# The cert is left base64 encoded, because that is how it is used in the K8s API. The token is decoded.
tmpfile=$(mktemp)
vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/testrunner-k8s-sa" "${tmpfile}"
result=$?
if [ $result -ne 0 -a "${k8senv}" = "integration" ]; then
    echo "No test runner credentials for target ${target}. Falling back to wsmtest credentials."
    vaultgetb64 "secret/dsde/terra/kernel/integration/wsmtest/testrunner-k8s-sa" "${tmpfile}"
    result=$?
fi
if [ $result -ne 0 ]; then
    echo "No test runner credentials for target ${target}."
else
    jq -r ".data[\"ca.crt\"]" "${tmpfile}" > "${outputdir}/testrunner-k8s-sa-key.txt"
    jq -r .data.token "${tmpfile}" | base64 --decode > "${outputdir}/testrunner-k8s-sa-token.txt"
fi

# CloudSQL setup for connecting to the backend database
# 1. Get the sqlproxy service account
# 2. Build the full db connection name
#    note: some instances do not have the full name, project, region. We default to the integration k8s values
# 3. Get the database information (user, pw, name) for db and stairway db
vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/sqlproxy-sa" "${outputdir}/sqlproxy-sa.json"
tmpfile=$(mktemp)
vaultget "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/postgres/instance" "${tmpfile}"
instancename=$(jq -r '.name' "${tmpfile}")
instanceproject=$(jq -r '.project' "${tmpfile}")
instanceregion=$(jq -r '.region' "${tmpfile}")
if [ "$instanceproject" == "null" ];
  then instanceproject=terra-kernel-k8s
fi
if [ "$instanceregion" == "null" ];
  then instanceregion=us-central1
fi
echo "${instanceproject}:${instanceregion}:${instancename}" > "${outputdir}/db-connection-name.txt"

vaultgetdb "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/postgres/db-creds" "db"
vaultgetdb "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/postgres/stairway-db-creds" "stairway-db"

# Write the test application configuration into the local-properties.yml file
if [ "$target" == "local" ]; then
  tmpfile=$(mktemp)
  vaultget "secret/dsde/terra/azure/dev/workspacemanager/managed-app-publisher" "${tmpfile}"
  clientid=$(jq -r '."client-id"' "${tmpfile}" )
  clientsecret=$(jq -r '."client-secret"' "${tmpfile}" )
  tenantid=$(jq -r '."tenant-id"' "${tmpfile}" )

  cat << EOF > "${outputdir}/local-properties.yml"
workspace:
  application:
    configurations:
      TestWsmApp:
        name: TestWsmApp
        description: WSM test application
        service-account: Elizabeth.Shadowmoon@test.firecloud.org
        state: operating
  azure:
    managed-app-client-id: ${clientid}
    managed-app-client-secret: ${clientsecret}
    managed-app-tenant-id: ${tenantid}
    sas-token-start-time-minutes-offset: 15
    sas-token-expiry-time-minutes-offset: 60
    sas-token-expiry-time-maximum-minutes-offset: 1440
  policy:
    base-path: https://tps.wsmtest-eng.bee.envs-terra.bio/
  cli:
    server-name: broad-dev
feature:
  tps-enabled: true
  temporary-grant-enabled: true
landingzone:
  sam:
    landing-zone-resource-users:
      - leonardo-dev@broad-dsde-dev.iam.gserviceaccount.com
      - workspace-wsmtest@terra-kernel-k8s.iam.gserviceaccount.com
      - Elizabeth.Shadowmoon@test.firecloud.org
pactbroker:
  url: localhost:9292

EOF
else
  cat /dev/null > "${outputdir}/local-properties.yml"
fi

# We made it to the end, so record the target and avoid redos
echo "$target" > "${outputdir}/target.txt"
