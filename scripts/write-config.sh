#!/bin/bash
#
# write-config.sh extracts configuration information from GSM and writes it to a set of files
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
Usage: $0 [<target>] [<outputdir>]"

  <target> can be:
    local - for testing against a local (bootRun) WSM
    dev - uses secrets from the dev environment
    help or ? - print this help
    clean - removes all files from the output directory
  If <target> is not specified, then use the envvar WSM_WRITE_CONFIG
  If WSM_WRITE_CONFIG is not specified, then use local

  <outputdir> defaults to "../config/" relative to the script. When run from the gradle rootdir, it will be
  in the expected place for automation.
EOF
 exit 1
}

# Get the inputs with defaulting
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null  && pwd )"
default_outputdir="${script_dir}/config"
default_target=${WSM_WRITE_CONFIG:-local}
target=${1:-$default_target}
outputdir=${2:-$default_outputdir}
fcenv="dev"

case $target in
    help | ?)
        usage
        ;;

    clean)
        rm "${outputdir}"/* &> /dev/null
        exit 0
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

# Fetch a secret from GSM
function dogsm {
    local dosecretproject=$1
    local dovaultpath=$2
    local dofilename=$3
    gcloud secrets versions access latest --project "${dosecretproject}" --secret "${dovaultpath}" > "${dofilename}"
}

# Read a GSM secret into an output file, decoding from base64
# To detect missing tokens, we need to capture the docker result before
# doing the rest of the pipeline.
function gsmgetb64 {
    secretproject=$1
    vaultpath=$2
    filename=$3
    fntmpfile=$(mktemp)
    dogsm "${secretproject}" "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r .key "${fntmpfile}" | base64 -d > "${filename}"
}

# Read a GSM secret into an output file
function gsmget {
    secretproject=$1
    vaultpath=$2
    filename=$3
    fntmpfile=$(mktemp)
    dogsm "${secretproject}" "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r . "${fntmpfile}" > "${filename}"
}

# Read database data from a GSM secret into a set of files
function gsmgetdb {
    secretproject=$1
    vaultpath=$2
    fileprefix=$3
    fntmpfile=$(mktemp)
    dogsm "${secretproject}" "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r '.db' "${fntmpfile}" > "${outputdir}/${fileprefix}-name.txt"
    jq -r '.password' "${fntmpfile}" > "${outputdir}/${fileprefix}-password.txt"
    jq -r '.username' "${fntmpfile}" > "${outputdir}/${fileprefix}-username.txt"
}

gsmget "broad-dsde-${fcenv}" "firecloud-sa" "${outputdir}/user-delegated-sa.json"

if [ "${target}" = "local" ]; then
    gsmget "broad-dsde-qa" "workspacemanager-wsmtest-sa" "${outputdir}/wsm-sa.json"
else
    gsmgetb64 "broad-dsde-${fcenv}" "wsm-sa-b64" "${outputdir}/wsm-sa.json"
fi

# Janitor SA is only available in integration
if [ "${target}" = "local" ]; then
    gsmgetb64 "broad-dsde-qa" "crljanitor-client-sa" "${outputdir}/janitor-client-sa.json"
else
    echo "No janitor credentials for target ${target}"
fi

if [ "${target}" = "local" ]; then
    gsmget "broad-dsde-qa" "buffer-client-tools-sa" "${outputdir}/buffer-client-sa.json"
else
    gsmgetb64 "broad-dsde-${fcenv}" "buffer-client-sa-b64" "${outputdir}/buffer-client-sa.json"
fi

# Policy SA is only stored in broad-dsde-qa although it's the SA for dev
gsmgetb64 "broad-dsde-qa" "tps-client-sa" "${outputdir}/policy-client-sa.json"

# Test Runner SA.
gsmgetb64 "broad-dsde-qa" "testrunner-sa-b64" "${outputdir}/testrunner-sa.json"

# Test Runner Kubernetes SA
#
# The testrunner K8s secret has a complex structure. At secret/.../testrunner-k8s-sa we have the usual base64 encoded object
# under data.key. When that is pulled out and decoded we get a structure with:
# { "data":  { "ca.crt": <base64-cert>, "token": <base64-token> } }
# The cert is left base64 encoded, because that is how it is used in the K8s API. The token is decoded.
tmpfile=$(mktemp)
gsmgetb64 "broad-dsde-${fcenv}" "testrunner-k8s-sa-b64" "${tmpfile}"
result=$?
if [ $result -ne 0 -a "${target}" = "local" ]; then
    echo "No test runner credentials for target ${target}. Falling back to wsmtest credentials."
    gsmgetb64 "broad-dsde-qa" "testrunner-k8s-sa-b64" "${tmpfile}"
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

if [ "${target}" = "local" ]; then
  gsmget "broad-dsde-qa" "workspacemanager-wsmtest-sqlproxy-sa" "${outputdir}/sqlproxy-sa.json"
else
  gsmgetb64 "broad-dsde-${fcenv}" "wsm-sqlproxy-sa-b64" "${outputdir}/sqlproxy-sa.json"
fi

tmpfile=$(mktemp)
if [ "${target}" = "local" ]; then
  gsmget "broad-dsde-qa" "wsmtest-wsm-postgres-instance" "${tmpfile}"
else
  gsmget "broad-dsde-${fcenv}" "wsm-postgres-instance" "${tmpfile}"
fi
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

if [ "${target}" = "local" ]; then
  gsmgetdb "broad-dsde-qa" "wsmtest-wsm-db-creds" "db"
  gsmgetdb "broad-dsde-qa" "wsmtest-wsm-stairway-db-creds" "stairway-db"
else
  gsmgetdb "broad-dsde-${fcenv}" "wsm-postgres-db-creds" "db"
  gsmgetdb "broad-dsde-${fcenv}" "wsm-stairway-db-creds" "stairway-db"
fi

# Write the test application configuration into the local-properties.yml file
if [ "$target" == "local" ]; then
  tmpfile=$(mktemp)
  gsmget "broad-dsde-dev" "wsm-managed-app-publisher" "${tmpfile}"
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
    base-path: https://tps.dsde-dev.broadinstitute.org/
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

EOF
else
  cat /dev/null > "${outputdir}/local-properties.yml"
fi

# We made it to the end, so record the target and avoid redos
echo "$target" > "${outputdir}/target.txt"
