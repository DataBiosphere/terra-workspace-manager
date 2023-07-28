# About
This little utility is used to run database operations in an Azure landing zone where the 
database is only accessible to landing zone managed identities and only on the
landing zone virtual network.

The utility is parameterized only by environment variables to make it easier to run from kubernetes
without risk of command injection.

This utility is designed to house multiple commands. Which command is run is determined by the
spring profile that is active. The commands are:
* `CreateDatabase` - creates a database with a given name and grant access to a managed identity. Used in the [CreateAzureDatabaseStep](../service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/azure/database/CreateAzureDatabaseStep.java). Environment variables:
  * `spring_profiles_active` - must be set to `CreateDatabase`
  * `NEW_DB_NAME` - the name of the database to create
  * `NEW_DB_USER_NAME` - the name of the user to create (good idea to make this the same as the managed identity)
  * `NEW_DB_USER_OID` - the OID of the manged identity
* `CreateDatabaseWithDbRole` - creates a database with a given name and grant access new role of the same name. Used in the [CreateAzureDatabaseStep](../service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/azure/database/CreateAzureDatabaseStep.java). Environment variables:
  * `spring_profiles_active` - must be set to `CreateDatabaseWithDbRole`
  * `NEW_DB_NAME` - the name of the database to create

Common environment variables:
* `DB_SERVER_NAME` - fully qualified name of the database server
* `ADMIN_DB_USER_NAME` - the name of the managed identity that is the admin of the database server

# Deployment
This utility is deployed as a docker image tagged with the git commit hash before the main WSM
tests are run. It is intended that the main WSM application uses the github hash found in its
version configuration to pull the correct version of this utility.