#!/bin/bash
#
# Input:
# $1 - version - the version number from inside gradle
# $2 - output file to write
#
version=$1
outputfile=$2

githash=$(git rev-parse HEAD)
gittag=$(git describe --tags)
dirty=$(git diff --quiet || echo '.dirty')
echo "version.build=$version" > "$outputfile"
echo "version.gitHash=$githash" >> "$outputfile"
echo "version.gitTag=$gittag$dirty" >> "$outputfile"
