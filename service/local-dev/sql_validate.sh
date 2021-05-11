#!/bin/bash

# validate postgres
echo "sleeping for 5 seconds during postgres boot..."
sleep 5
PGPASSWORD=wmpwd psql --username wmuser -d wm -c "SELECT VERSION();SELECT NOW()"
