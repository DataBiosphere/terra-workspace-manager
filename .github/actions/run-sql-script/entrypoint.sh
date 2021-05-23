#!/bin/bash
#
# Since we get the inputs from the action, we don't need to do a lot of sanity checking
#
# Arguments:
#  DB_USERNAME - username to login to CloudSQL
#  DB_PASSWORD - password to login to CloudSQL
#  DB_NAME     - database name to connect to
#  DB_INSTANCE - full connection name: <project>:<region>:<cloudsql-instance>
#  CLOUDSQL_SA_FILE - location in the container where the invoker put the cloudsql SA JSON credentials file
#  SCRIPT      - location in the container where the invoker put the Postgresql script to execute on the database

DB_USERNAME=$1
DB_PASSWORD=$2
DB_NAME=$3
DB_INSTANCE=$4
CLOUDSQL_SA_FILE=$5
SCRIPT=$6

# We can make this an arg if need be, but I think the scope works fine here inside the container
port=5434

# Launch the cloud sql proxy and wait a bit for it to start
cloud_sql_proxy -instances=${DB_INSTANCE}=tcp:$port &
proxypid=$!
sleep 5

# Run the script through psql
PGPASSWORD="${DB_PASSWORD}" psql "host=127.0.0.1 port=$port sslmode=disable dbname=${DB_NAME} user=${DB_USERNAME}" -f ${SCRIPT}

# Stop the proxy - not sure this is needed in the docker context. It helps when testing the script.
sleep 2
kill -9 $proxypid




