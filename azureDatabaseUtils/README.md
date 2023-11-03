# About
This little utility is used to run database operations in an Azure landing zone where the 
database is only accessible to landing zone managed identities and only on the
landing zone virtual network. See [AzureDatabaseUtilsRunner](../service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/azure/database/AzureDatabaseUtilsRunner.java)
for the code that launches this utility.

The utility is parameterized only by environment variables to make it easier to run from kubernetes
without risk of command injection.

This utility is designed to house multiple commands. 
Each command must be idempotent to allow for stairway retries. 
Which command is run is determined by the spring profile that is active. The commands are:
* `CreateDatabaseWithDbRole` - creates a database with a given name and grant access new role of the same name. Environment variables:
  * `spring_profiles_active` - must be set to `CreateDatabaseWithDbRole`
  * `NEW_DB_NAME` - the name of the database to create
* `CreateNamespaceRole` - creates a namespace role with a given name and allows a managed identity to authenticate to that role. Environment variables:
  * `spring_profiles_active` - must be set to `CreateNamespaceRole`
  * `NAMESPACE_ROLE` - the name of the role to create
  * `MANAGED_IDENTITY_OID` - the OID of the manged identity
  * `DATABASE_NAMES` - a comma separated list of databases to grant access to
* `DeleteNamespaceRole` - deletes a namespace role with a given name. Environment variables:
  * `spring_profiles_active` - must be set to `DeleteNamespaceRole`
  * `NAMESPACE_ROLE` - the name of the role to delete
* `RevokeNamespaceRoleAccess` - revokes login access and terminates all sessions for namespace role. Environment variables:
  * `spring_profiles_active` - must be set to `RevokeNamespaceRoleAccess`
  * `NAMESPACE_ROLE` - the name of the role to delete
* `RestoreNamespaceRoleAccess` - restores login access for namespace role. Environment variables:
  * `spring_profiles_active` - must be set to `RestoreNamespaceRoleAccess`
  * `NAMESPACE_ROLE` - the name of the role to delete
* `TestDatabaseConnect` - A command to be used in connected tests to verify database permissions. Environment variables:
  * `spring_profiles_active` - must be set to `TestDatabaseConnect`
  * `CONNECT_TO_DATABASE` - the name of the database to connect to
  * `ADMIN_DB_USER_NAME` - the name of the user to connect as

Common environment variables:
* `DB_SERVER_NAME` - fully qualified name of the database server
* `ADMIN_DB_USER_NAME` - the name of the managed identity that is the admin of the database server

# Deployment
This utility is deployed as a docker image tagged with the git commit hash before the main WSM
tests are run. It is intended that the main WSM application uses the github hash found in its
version configuration to pull the correct version of this utility.

# Development
To deploy an image of this utility for your git commit hash, run the following
```bash
./gradlew :azureDatabaseUtils:jibDockerBuild --image=us.gcr.io/broad-dsp-gcr-public/azure-database-utils:$(git rev-parse HEAD) -Djib.console=plain
docker push us.gcr.io/broad-dsp-gcr-public/azure-database-utils:$(git rev-parse HEAD) 
```
