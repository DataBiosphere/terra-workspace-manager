This little application is used to create a database in an Azure landing zone.
It is used in the [CreateAzureDatabaseStep](../service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/azure/database/CreateAzureDatabaseStep.java).
It is a separate application because it needs to be able to run in a docker container on
the landing zone vnet as the database server admin manged identity.