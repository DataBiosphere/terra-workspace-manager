# Getting Started with Workspace Manager

Note: this document is being written during a time when code is rapidly evolving. It's 
possible that this will be out of date regularly but we should maintain it as best we can until
development stabilizes.


### Prerequisites:

Install Postgres: https://www.postgresql.org/download/
Recommended: read the README to understand the general structure of the service


## Database Configuration

Workspace Manager relies on two databases: one for the app itself, and one for Stairway. We will also
need a DB for the unit tests.

In order to set these up, run the following `psql` commands:

Create the DB used for unit tests:

```
CREATE DATABASE testdb;
CREATE ROLE dbuser WITH LOGIN ENCRYPTED PASSWORD 'dbpwd';
```

Create the DB for Stairway:

```
CREATE DATABASE stairwaylib;
CREATE ROLE stairwayuser WITH LOGIN ENCRYPTED PASSWORD 'stairwaypwd';
```

Create the DB for the app:

```
CREATE DATABASE wm;
CREATE ROLE wmuser WITH LOGIN ENCRYPTED PASSWORD 'wmpwd';
```

At some point, we will connect this to a CloudSQL instance but for local dev purposes having the 
option to use a local DB instead makes sense.


## Running Tests

To run unit tests:

`./gradlew test`

Currently the unit tests hit dev Sam. This is obviously bad practice but until we have implemented 
mocks, this will.


## Running Workspace Manager

To run locally, you'll first need to export a few environment variables:

```
export DATABASE_USER=wmuser
export DATABASE_USER_PASSWORD=wmpwd
export DATABASE_NAME=wm
export DATABASE_USER=stairwayuser
export DATABASE_USER_PASSWORD=stairwaypwd
export DATABASE_NAME=stairwaylib
```

To run the application:

`./gradlew bootRun`

Then navigate to the Swagger at:

`http://localhost:8080/api/swagger-ui.html`


## Other

You may also want to periodically rebuild and refresh any auto-generated code:

`./gradlew clean build`

`./gradlew openApiGenerate`

TODO: It would be nice to have a kickstart script that new devs can run that configures much of this, but
for now it will be documented here to help us know what should be scripted/simplified.

## Status as of 2/28:
The repo is currently in a usable state, but doesn't meet all the goals of
AS-197. The missing requirements are:
1. Stairway is configured and initialized, but never used. We need to move
CreateService code into steps/flights and actually use Stairway.
2. The `create` endpoint is currently synchronous - we expect most endpoints
will be async. This means changing the existing endpoint to return a pollable
endpoint, and actually building the pollable endpoint.
3. README is copied directly from kernel-service-poc and needs updates.