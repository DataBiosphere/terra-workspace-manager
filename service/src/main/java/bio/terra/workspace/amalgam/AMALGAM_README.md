# Amalgam Package

_NOTE_: This is a deprecated pattern and should not be used going forward.

Amalgam (noun) _a mixture or blend._

The WSM service app is configured to contain other Terra components. There are two reasons why we want to do this:
1. It requires at least a person-month to deploy a new component into the Broad and Verily deployments. We can deploy and use a new component faster by skipping the new deployment.
2. Some components have the same usage pattern and scaling requirements at WSM. There is little motivation for additional service deployments.

# How To

Terra Policy Service (TPS) is the first component we are doing this way. I expect it will eventually be its own service 
because its scaling properties are different from WSM. These are my "How I did it" notes.

## Component Library Structure
The component is built as a separate library with a separate repo. The TPS `service` package is built into a library.
It uses Spring boot, but is packaged as a plain library and not as a Spring boot application. I have a `testharness` 
in TPS that provides the Spring application and drives JUnit testing of the code.

To fit into WSM, you should use the Terra Common Library (TCL) database configuration. That allows you to keep
the database configuration in the library and only add the minimum of code to WSM.

## WSM Package Structure
Create a package under `service/src/main/java/.../amalgam` for your library. Most of your WSM hook-in code will go here.

## Gradle
Add a dependency in `service/gradle/dependencies.gradle` to depend on your component library

## REST API
Your REST API should be defined in OpenAPI 3.0 format. You will need a file that specifies only
- `paths`
- `components/parameters`
- `components/schemas`
- `components/responses`

Note the common responses and other common MC Terra parts are available in the `openapi` sub-project
in the WSM project.

Put your API file in `openapi/src/parts`.

## Local Dev Database Creation
For local testing, the WSM tooling uses `service/local-dev/local-postgres-init.sql` to create the
databases. This script is used when running Postgres in a container. If you run Postgres on your
local machine, then you need to run the script against your local machine.

Add your database creation steps to that database. For example,
```
CREATE DATABASE policy_db;
```

## Scan and Initialize Your Library
Assuming your library uses Spring, you will need to explicitly add your library package to
the `@ComponentScan` in `app/Main.java`. That is how Spring knows to look at those packages in your
library and find the beans.

Your component library probably has initialization tasks, like initializing your database.
Add a call to your initialization method in `app/StartupInitializer`. 

## Testing
All component testing should be done using a test harness on your library.
Basic tests to validate integration with WSM can be added to `test/java/.../amalgam/<your package>`.


