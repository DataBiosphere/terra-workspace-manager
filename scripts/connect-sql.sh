#!/bin/bash
#
# Launch a docker running the CloudSQL proxy.
# Connect to a database with psql.
# When psql exits, kill the proxy.
#
# Input:
#  wsm or stairway - which database to connect to
#
# You must have run the write-config script to set up all of the connection information

scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
configdir="$( cd "${scriptdir}/../config" &> /dev/null && pwd )"

# Check that there is some configuration
if [ ! -e "${configdir}/target.txt" ]; then
   echo "No configuration found. Run write-config.sh"
   exit 1
fi
target=$(<"${configdir}/target.txt")

# Setup depending on the input
case $1 in
  wsm)
    db=$(<"${configdir}/db-name.txt")
    dbuser=$(<"${configdir}/db-username.txt")
    dbpw=$(<"${configdir}/db-password.txt")
    ;;
  stairway)
    db=$(<"${configdir}/stairway-db-name.txt")
    dbuser=$(<"${configdir}/stairway-db-username.txt")
    dbpw=$(<"${configdir}/stairway-db-password.txt")
    ;;
  *)
    echo "Specify wsm or stairway to choose which database to connect to"
    exit 1
    ;;
esac

port=5434
echo "Connecting to $db in target $target"
dod=$( "${scriptdir}/launch-sql-proxy.sh" "$port" )
echo "Launched docker container: ${dod}"

# Setup cleanup of the docker container
function kill_docker()
{
    if [ -n "${dod}" ]; then
        echo "Stopping the CloudSQL proxy docker: ${dod}"
        docker kill "${dod}"
    fi
}
trap kill_docker EXIT

# I hope this works and is interactive. We will see...
PGPASSWORD="${dbpw}" psql "host=127.0.0.1 port=${port} sslmode=disable dbname=${db} user=${dbuser}"

