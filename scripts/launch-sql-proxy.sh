#!/bin/bash
#
# This script is used by other scripts to launch the google cloud sql proxy in a docker container.
# It relies on the config directory being populated.
#
# Output:
#   echos the docker container id to stdout for later cleanup
#
# Usage:
#   dockerid=$(./scripts/launch-sql-proxy.sh)
# 
port=$1
if [ -z "$port" ]; then
    >&2 echo "You must specify a port to use for the cloud sql proxy"
    exit 1
fi

scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
configdir="$( cd "${scriptdir}/../config" &> /dev/null && pwd )"
instance=$(<"${configdir}/db-connection-name.txt")
dod=$(docker run --rm -d \
             -v "${configdir}":/config \
             -p 127.0.0.1:${port}:${port} \
             gcr.io/cloudsql-docker/gce-proxy:latest \
             /cloud_sql_proxy \
             -instances=${instance}=tcp:0.0.0.0:${port} \
             -credential_file=/config/sqlproxy-sa.json)
sleep 3
echo $dod

