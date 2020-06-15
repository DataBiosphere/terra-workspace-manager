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

In order to set these up, run the following command, which will create the DB's and users for unit tests, Stairway, and the app itself:

```sh
psql -f local-dev/local-postgres-init.sql
```

At some point, we will connect this to a CloudSQL instance but for local dev purposes having the 
option to use a local DB instead makes sense.


## Running Tests

To run unit tests:  
`./gradlew unitTest`  
  
To run integration tests: 
- Run `render_config.sh` to render configs. See `NOTES` below for more info.   
- Run `./gradlew integrationTest` to kick the tests off  
 
To run all tests:  
`./gradlew test`

NOTE (Some of this will likely change as we grow integration tests). Integration test assumes that:
1. You have generated an access token from GitHub and saved the token in your home directory with the filename `.vault-token`
2. Default environment for rendering configs is `dev`. You can pass arguments to the script to target different environments.
3. Test user has been registered in an existing WSM environment. Currently, the test user is registered in `dev` environment where WSM is currently deployed. You don't need to take any action in this step, unless the dev environment changes for some reason and the user no longer exists there. 


## Running Workspace Manager

To run locally, you'll first need to export a few environment variables:

```sh
export DATABASE_USER=wmuser
export DATABASE_USER_PASSWORD=wmpwd
export DATABASE_NAME=wm
export STAIRWAY_DATABASE_USER=stairwayuser
export STAIRWAY_DATABASE_USER_PASSWORD=stairwaypwd
export STAIRWAY_DATABASE_NAME=stairwaylib
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
