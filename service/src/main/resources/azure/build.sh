#!/bin/bash
gittop=$(git rev-parse --show-toplevel)
target=$gittop/build/azure
source=$gittop/service/src/main/resources/azure
mkdir -p $target
rm -f $target/app.zip
cp ${source}/terralogo.png ${target}/.
cd ${source}/marketplaceTemplate
zip -vr ${target}/app.zip . -x "*.DS_Store"
cd ..

