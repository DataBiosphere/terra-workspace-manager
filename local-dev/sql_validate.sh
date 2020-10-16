#!/bin/bash

# validate postgres
echo "sleeping for 5 seconds during postgres boot..."
sleep 5
export PGPASSWORD=wmpwd
psql --username wmuser --host=postgres --port=5432 -d wm -c "SELECT VERSION();SELECT NOW()"
