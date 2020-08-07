#!/bin/bash

## Create database in Influxdb

namespace="$1"
database="$2"

[ -n "$namespace" ] || read -p 'Enter the Jmeter Namespace: ' namespace

kubectl get namespace | grep $namespace >> /dev/null

if [ $? != 0 ];
then
    echo "Namespace does not exist in the kubernetes cluster"
    echo ""
    echo "Below is the list of namespaces in the kubernetes cluster"

    kubectl get namespaces
    echo ""
    echo "Please check and try again"
    exit
fi

##Wait until Influxdb Deployment is up and running
ntries=3
n=0
influxdb_status=`kubectl get po -n $namespace | grep influxdb | awk '{print $3}' | grep Running`
while [[ "$influxdb_status" != "Running" && $n < $ntries ]]
do
  echo $influxdb_status
  sleep 10s
  influxdb_status=`kubectl -n $namespace get po | grep influxdb | awk '{print $3}' | grep Running`
  ((n++))
done

if [ $n -eq $ntries ]
then
  echo "Time out waiting for running instance of Influxdb"
  exit
fi

echo "Creating Influxdb Database"

[ -n "$database" ] || read -p 'Enter the Database name : ' database

influxdb_pod=`kubectl -n $namespace get po | grep influxdb | awk '{print $1}'`

kubectl -n $namespace exec -ti $influxdb_pod -- influx -execute 'SHOW DATABASES' | grep $database >> /dev/null

if [ $? == 0 ];
then
    echo "Database already exist"
    echo ""
    echo "Below is the list of databases on the Database server"

    kubectl -n $namespace exec -ti $influxdb_pod -- influx -execute 'SHOW DATABASES'
    echo ""
    echo "Please try enter a new database name"
    exit
fi

kubectl -n $namespace exec -ti $influxdb_pod -- influx -execute "CREATE DATABASE $database"

## Create the influxdb datasource in Grafana

echo "Creating the Influxdb data source"

grafana_pod=`kubectl -n $namespace get po | grep grafana-deployment | awk '{print $1}'`

## Make load test script in Jmeter master pod executable

#Get Master pod details

director_pod=`kubectl -n $namespace get po | grep director | awk '{print $1}'`

# Workaround for the read only attribute of config map
kubectl -n $namespace exec -ti $director_pod -- cp -rf /load_test /jmeter/load_test

kubectl -n $namespace exec -ti $director_pod -- chmod 755 /jmeter/load_test

#Get the name of the influxDB svc

influxdb_svc=`kubectl -n $namespace get svc | grep influxdb | awk '{print $1}'`

kubectl -n $namespace exec -ti $grafana_pod -- curl 'http://admin:admin@127.0.0.1:3000/api/datasources' -X POST -H 'Content-Type: application/json;charset=UTF-8' --data-binary '{"name":"'$database'db","type":"influxdb","url":"http://'$influxdb_svc':8086","access":"proxy","isDefault":true,"database":"'$database'","user":"admin","password":"admin"}'