#!/usr/bin/env bash
# This script creates a Python virtual environment. For this configuration to work
# you must have the 'python3' command available.
toolsDir=$1
outDir=$2
python3installed=$(which python3 | wc -l)
if [ ${python3installed} != "1" ]; then
  echo "ERROR: python3 must be installed"
  exit 1
fi
virtualenvinstalled=$(pip3 list | grep "virtualenv" | wc -l)
if [ ${virtualenvinstalled} != "1" ]; then
  echo "ERROR: virtualenv must be installed"
  exit 1
fi
python3 -m virtualenv ${outDir}/venv
source ${outDir}/venv/bin/activate
python3 -m pip install -r "${toolsDir}/requirements.txt"
deactivate
