# Getting Started with Workspace Manager

Note: this document is being written during a time when code is rapidly evolving. It's possible that this will be out of date regularly but we should maintain it as best we can until development stabilizes.

## Setup

### Prerequisites:

- Install Postgres 13.1: https://www.postgresql.org/download/
  - [The app](https://postgresapp.com/downloads.html) may be easier, just make sure to download the right version. It'll manage things for you and has a useful menulet where the server can be turned on and off. Don't forget to create a server if you go this route.
- Install AdoptOpenJDK Java 11 (Hotspot). Here's an easy way on Mac, using [jEnv](https://www.jenv.be/) to manage the active version:

    ```sh
    brew install jenv
    # follow postinstall instructions to activate jenv...
    
    # to add previously installed versions of Java to jEnv, list them:
    # /usr/libexec/java_home -V
    # and then add them:
    # jenv add /Library/Java/JavaVirtualMachines/<JAVA VERSION HERE>/Contents/Home

    # follow instructions from https://github.com/AdoptOpenJDK/homebrew-openjdk to install adoptopenjdk11:
    brew tap AdoptOpenJDK/openjdk
    brew cask install adoptopenjdk11

    jenv add /Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home
    ```
- Recommended: read the [README](README.md) to understand the general structure of the service

**NOTE**: You may encounter issues with the application when running an unexpected version of Java. So make sure you are running `AdoptOpenJDK Java 11 (Hotspot)` as specified above.     


### Database Configuration
Workspace Manager relies on two databases: one for the app itself, and one for Stairway. We will also need a DB for the unit tests.

#### Option A: Docker Postgres
##### Running the Postgres Container
To start a postgres container configured with the necessary databases:
```sh
./local-dev/run_postgres.sh start
```
To stop the container:
```sh
./local-dev/run_postgres.sh stop
```
Note that the contents of the database is not saved between container runs.

##### Connecting to the Postgres Container
Use `psql` to connect to databases within the started database container, e.g. for database `wm` users `wmuser` with password `wmpwd`:
```sh
PGPASSWORD=wmpwd psql postgresql://127.0.0.1:5432/wm -U wmuser
```

#### Option B: Local Postgres 
##### Database Configuration

To set up Workspace Manager's required database, run the following command, which will create the DB's and users for unit tests, Stairway, and the app itself:

```sh
psql -f local-dev/local-postgres-init.sql
```

At some point, we will connect this to a CloudSQL instance but for local dev purposes having the option to use a local DB instead makes sense.

### IntelliJ Setup

1. Open the repo normally (File -> Open)
2. In project structure (the folder icon with a little tetromino over it in the upper right corner), make sure the project SDK is set to Java 11. If not, IntelliJ should detect it on your system in the dropdown, otherwise click "Add JDK..." and navigate to the folder from the last step.
3. See some optional tips below in the ["Tips"](#tips) section.

## Running

### Running Tests

To run unit tests:

```sh
./gradlew unitTest
```
  
To run connected tests:

```shell script
./render_config.sh # First time only
./gradlew connectedTest
```
To run integration tests: (see **NOTE** below)

```sh
./render_config.sh # First time only
./gradlew integrationTest
```
 
To run all tests:

```sh
./gradlew test
```

**NOTE** (Some of this will likely change as we grow integration tests). Integration test assumes that:
1. You have generated an access token from GitHub and saved the token in your home directory with the filename `.vault-token`
2. Default environment for rendering configs is `dev`. You can pass arguments to the script to target different environments.
3. Test user has been registered in an existing WSM environment. Currently, the test user is registered in `dev` environment where WSM is currently deployed. You don't need to take any action in this step, unless the dev environment changes for some reason and the user no longer exists there. 


### Running Workspace Manager

To run locally, you'll first need to render configs (if you haven't already): 

```sh
./render_config.sh # First time only
```

To run the application:

```sh
./gradlew bootRun
```

Then navigate to the Swagger:  
http://localhost:8080/swagger-ui.html

### Other

You may also want to periodically rebuild and refresh any auto-generated code:

```sh
./gradlew clean build -x test
```

TODO: It would be nice to have a kickstart script that new devs can run that configures much of this, but for now it will be documented here to help us know what should be scripted/simplified.

### Tips
- Check out [gdub](https://github.com/gdubw/gdub), it'll save you typing `./gradlew` over and over, and also takes care of knowing when you're not in the root directory so you don't have to figure out the appropriate number of `../`s.
- In IntelliJ, instead of running the local server with `bootRun`, use the `Main` Spring Boot configuration that IntelliJ auto-generates. To edit it, click on it (in the upper right of the window), and click `Edit Configurations`.
    - For readable logs, put `human-readable-logging` in the `Active Profiles` field. 
    - You can get live-ish reloading of for the local Swagger UI by adding the following override parameter: `spring.resources.static-locations:file:src/main/resources/api`. It's not true live reloading, you still have to refresh the browser, but at least you don't have to restart the server.
