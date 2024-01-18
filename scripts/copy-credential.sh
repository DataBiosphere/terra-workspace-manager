#!/usr/bin/env bash

if [[ -z "${GOOGLE_APPLICATION_CREDENTIALS}" ]]; then
  echo "GOOGLE_APPLICATION_CREDENTIALS is not set"
  exit 1
fi

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null  && pwd )"
outputdir="${script_dir}/config"

echo "Copying creds from ${GOOGLE_APPLICATION_CREDENTIALS} to ${outputdir}/user-delegated-sa.json"
cp "${GOOGLE_APPLICATION_CREDENTIALS}" "${outputdir}/user-delegated-sa.json"

result=$?
if [ $result -ne 0 ]; then
  exit $result
fi
echo "Done"
