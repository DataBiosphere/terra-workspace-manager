#!/bin/bash
#
# Since we get the inputs from the action, we don't need to do a lot of sanity checking
#
# Arguments:
#  DB_USERNAME - username to login to CloudSQL
#  DB_PASSWORD - password to login to CloudSQL
#  DB_NAME     - database name to connect to
#  DB_INSTANCE - full connection name: <project>:<region>:<cloudsql-instance>
#  SQLPROXY_SA_FILE - path to the SA credentials to use for the proxy
#  SCRIPT      - location in the container where the invoker put the Postgresql script to execute on the database

echo "We are inside of a running entrypoing.sh"

DB_USERNAME=$1
DB_PASSWORD=$2
DB_NAME=$3
DB_INSTANCE=$4
SQLPROXY_SA_FILE=$5
SCRIPT=$6

creddir=$(cd "${SQLPROXY_SA_FILE%/*}" && pwd)
credfile="${SQLPROXY_SA_FILE##*/}"

# We can make this an arg if need be, but I think the scope works fine here inside the container
port=5434

echo "Launching the CloudSQL proxy"
dod=$(docker run -d \
      -v ${creddir}:/config \
      -p 127.0.0.1:${port}:${port} \
      gcr.io/cloudsql-docker/gce-proxy:latest /cloud_sql_proxy \
      -instances=${DB_INSTANCE} \
      -credential_file=/config/${credfile})
sleep 5

echo "Running the script via psql"
PGPASSWORD="${DB_PASSWORD}" psql "host=127.0.0.1 port=$port sslmode=disable dbname=${DB_NAME} user=${DB_USERNAME}" -f ${SCRIPT}

# Not sure we need to stop the proxy in the docker context. It helps when testing the script.
sleep 2
echo "Stopping the CloudSQL proxy"
docker kill $dod

