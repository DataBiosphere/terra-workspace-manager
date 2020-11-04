#!/usr/bin/env bash
# Start up a postgres container with initial user/database setup.
POSTGRES_VERSION=12.3

start() {
    echo "attempting to remove old $CONTAINER container..."
    docker rm -f $CONTAINER

    # start up postgres
    echo "starting up postgres container..."
    BASEDIR=$(dirname "$0")
    docker create --name $CONTAINER --rm -e POSTGRES_PASSWORD=password -p "$POSTGRES_PORT:5432" postgres:$POSTGRES_VERSION
    docker cp $BASEDIR/local-postgres-init.sql $CONTAINER:/docker-entrypoint-initdb.d/docker_postgres_init.sql
    docker start $CONTAINER

    # validate postgres
    echo "running postgres validation..."
    docker exec $CONTAINER sh -c "$(cat $BASEDIR/sql_validate.sh)"
    if [ 0 -eq $? ]; then
        echo "postgres validation succeeded."
    else
        echo "postgres validation failed."
        exit 1
    fi

}

stop() {
    echo "Stopping docker $CONTAINER container..."
    docker stop $CONTAINER || echo "postgres stop failed. container already stopped."
    docker rm -v $CONTAINER
    exit 0
}

CONTAINER=postgres
COMMAND=$1
POSTGRES_PORT=${2:-"5432"}

if [ ${#@} == 0 ]; then
    echo "Usage: $0 stop|start"
    exit 1
fi

if [ $COMMAND = "start" ]; then
    start
elif [ $COMMAND = "stop" ]; then
    stop
else
    exit 1
fi
