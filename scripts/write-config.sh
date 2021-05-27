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
#   --------------------------+-------------------------------------------------------------------------
#   buffer-client-sa.json     | Creds to access Buffer service
#   --------------------------+-------------------------------------------------------------------------
#   db-connection-name.txt    | Connection string for CloudSQL proxy
#   --------------------------+-------------------------------------------------------------------------
#   db-name.txt               | Database name
#   --------------------------+-------------------------------------------------------------------------
#   db-password.txt           | Database password
#   --------------------------+-------------------------------------------------------------------------
#   db-username.txt           | Database username
#   --------------------------+-------------------------------------------------------------------------
#   janitor-client-sa.json    | Creds to access Janitor service
#   --------------------------+-------------------------------------------------------------------------
#   sqlproxy-sa.json          | SA of the CloudSQL proxy
#   --------------------------+-------------------------------------------------------------------------
#   stairway-db-name.txt      | Stairway database name
#   --------------------------+-------------------------------------------------------------------------
#   stairway-db-password.txt  | Stairway database password
#   --------------------------+-------------------------------------------------------------------------
#   stairway-db-username.txt  | Stairway database username
#   --------------------------+-------------------------------------------------------------------------
#   target.txt                | the target that generated this set of config files. Allows the script 
#                             | to skip regenerating the environment on a rerun.
#   --------------------------+-------------------------------------------------------------------------
#   testrunner-sa.json        | SA for running TestRunner
#   --------------------------+-------------------------------------------------------------------------
#   user-delegated-sa.json    | Firecloud SA used to masquerade as test users
#   --------------------------+-------------------------------------------------------------------------
#   wsm-sa.json               | SA that the WSM application runs as
#   --------------------------+-------------------------------------------------------------------------

function usage {
  cat <<EOF
Usage: $0 [<target>] [<vaulttoken>] [<outputdir>]"

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

  <outputdir> defaults to "config/", so when run in the gradle rootdir, it will be
  in the expected place for automation.
EOF
 exit 1
}

# Get the inputs with defaulting
default_target=${WSM_WRITE_CONFIG:-local}
target=${1:-$default_target}
vaulttoken=${2:-$(cat "$HOME"/.vault-token)}
outputdir=${3:-config}

# The vault paths are irregular, so we map the target into three variables:
# k8senv    - the kubernetes environment: alpha, staging, or integration
# namespace - the namespace in the k8s env: alpha, staging, dev, or the target for personal environments
# fcenv     - the firecloud delegated service account environment: dev, alpha, staging

case $target in
    help | ?)
        usage
        ;;

    clean)
        rm "$(pwd)/${outputdir}/*" &> /dev/null
        exit 0
        ;;

    local)
        k8senv=integration
        namespace=wsmtest
        fcenv=dev
        ;;

    dev)
        k8senv=integration
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

# If there a config and it matches, don't regenerate
if [ -e "${outputdir}/target.txt" ]; then
    oldtarget=$(<"${outputdir}/target.txt")
    if [ "$oldtarget" = "$target" ]; then
        echo "Config for $target already written"
        exit 0
    fi
fi

# Read a vault path into an output file, decoding from base64
function vaultgetb64 {
    vaultpath=$1
    filename=$2
    docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:consul-0.20.0 \
        vault read -format=json "${vaultpath}" | jq -r .data.key | base64 -d > "${outputdir}/${filename}"
}

# Read a vault path into an output file
function vaultget {
    vaultpath=$1
    filename=$2
    docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:consul-0.20.0 \
        vault read -format=json "${vaultpath}" | jq -r .data > "${outputdir}/${filename}"
}

# Read database data from a vault path into a set of files
function vaultgetdb {
    vaultpath=$1
    fileprefix=$2
    fil="${outputdir}/dbtmp.json"
    docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:consul-0.20.0 \
        vault read -format=json "${vaultpath}" | jq -r .data > "${fil}"
    jq -r '.db' "${fil}" > "${outputdir}/${fileprefix}-name.txt"
    jq -r '.password' "${fil}" > "${outputdir}/${fileprefix}-password.txt"
    jq -r '.username' "${fil}" > "${outputdir}/${fileprefix}-username.txt"
    rm "${fil}"
}

vaultget "secret/dsde/firecloud/${fcenv}/common/firecloud-account.json" "user-delegated-sa.json"
vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/app-sa" "wsm-sa.json"

# The magic tokens for access to Buffer Service, Janitor, and TestRunner are in fixed places
vaultgetb64 "secret/dsde/terra/kernel/integration/tools/crl_janitor/client-sa" "janitor-client-sa.json"
vaultgetb64 "secret/dsde/terra/kernel/integration/tools/buffer/client-sa" "buffer-client-sa.json"
vaultgetb64 "secret/dsde/terra/kernel/integration/common/testrunner/testrunner-sa" "testrunner-sa.json"

# Test runner K8s configuration
# TODO (PF-744): The setup for this is inconsistent. The usage is unknown. So we will skip it for now.
#vaultgetb64 "secret/dsde/terra/kernel/integration/${namespace}/testrunner-k8s-sa" "trtmp.json"
#fil=${outputdir}/trtmp.json
#cat ${fil} | jq -r ".data[\"ca.crt\"]" > ${outputdir}/testrunner-k8s-sa-client-key-data.crt
#cat ${fil} | jq -r .data.token | base64 --decode > ${outputdir}/testrunner-k8s-sa-token
#rm ${fil}

# CloudSQL setup for connecting to the backend database
# 1. Get the sqlproxy service account
# 2. Build the full db connection name
#    note: some instances do not have the full name, project, region. We default to the integration k8s values
# 3. Get the database information (user, pw, name) for db and stairway db
vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/sqlproxy-sa" "sqlproxy-sa.json"
vaultget "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/postgres/instance" "dbtmp.json"
fil="${outputdir}/dbtmp.json"
instancename=$(jq -r '.name' "${fil}")
instanceproject=$(jq -r '.project' "${fil}")
instanceregion=$(jq -r '.region' "${fil}")
if [ "$instanceproject" == "null" ];
  then instanceproject=terra-kernel-k8s
fi
if [ "$instanceregion" == "null" ];
  then instanceregion=us-central1
fi
echo "${instanceproject}:${instanceregion}:${instancename}" > "${outputdir}/db-connection-name.txt"
rm "${fil}"
vaultgetdb "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/postgres/db-creds" "db"
vaultgetdb "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/postgres/stairway-db-creds" "stairway-db"

# We made it to the end, so record the target and avoid redos
echo "$target" > "${outputdir}/target.txt"

