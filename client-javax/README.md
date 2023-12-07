# Workspace Manager Client Library for Javax

This library is provided for backwards compatibility with services that have not yet migrated to use
jakarta. This can be deleted once all services have migrated.

## Publish a new version
This should only be done by CI, not by a developer. 

To publish a new version of the client library:

1. Optionally, update the version number in [the top-level build.gradle](../build.gradle)
2. `cd workspace-manager-client`
3. run `./publish.sh`

## Publish a local version

To publish a local version that you can use locally -- for example, if you want to use some changes that
haven't been merged yet -- [see here.](https://github.com/DataBiosphere/terra-common-lib/blob/develop/README.md#local-testing)