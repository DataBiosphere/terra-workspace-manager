#!/usr/bin/env bash
# rootDir - the root of the github clone
# outDir - the directory where the output should be written
toolsDir=$1
srcDir=$2
outDir=$3
echo "Working from:\n  toolsDir = ${toolsDir}\n  srcDir = ${srcDir}\n  outDir = ${outDir}\n"

# Setup the local python virtual environment with python and all libraries installed
echo "Activating virtual environment"
source ${outDir}/venv/bin/activate
which python3
echo "Merging openapi"
# Run the python script to generate the openapi result
python3 ${toolsDir}/merge_openapi.py \
  --main ${srcDir}/openapi_main.yaml \
  --apidir ${srcDir} \
  --outdir ${outDir}
