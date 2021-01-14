# workspace-manager-clienttests
This Gradle project contains Test Runner tests written with the Workspace Manager Service client library.

The Test Runner library [GitHub repository](https://github.com/DataBiosphere/terra-test-runner) has documentation for
how to write and execute tests.

#### Run a test
The workspace manager tests require the appropriate service account keys to be available in the `rendered/` folder.

Run the following command from the `workspace-manager-clienttests` directory to retrieve the required keys.
Note: `render-config.sh` assumes a valid `.vault-token` in your `$HOME` directory. Please refer to the following document on how to generate a `vault-token`
 
 https://github.com/broadinstitute/dsde-toolbox#authenticating-to-vault 

```
./render-config.sh
```

The test runner task `runTest` can be used to launch tests in two different modes
* Run individual test
* Run a test suite

#### Run individual test
To run an individual test case, specify the path of the test configuration under the `configs` directory as shown in the following examples.
Developers can incorporate these individual test scenarios as use cases in their test workflows.
Please refer to the GitHub Action workflow use cases in the `Run a test suite` section below.

Integration Test
```
./gradlew  runTest --args="configs/integration/BasicAuthenticated.json /tmp/TR"
```

Perf Test
```
./gradlew  runTest --args="configs/perf/BasicAuthenticated.json /tmp/TR"
```

#### Run a test suite
To run a test suite, specify the path of the test configuration under the `suites` directory as shown in the following example.
NB: The `BasicIntegration.json` and `BasicNightlyPerf.json` are sample test suite configurations for Integration and Perf tests.
See `test-runner-integration.yml` and `test-runner-nightly-perf.yml` in `.github/workflows` for use cases.

Integration Test
```
./gradlew  runTest --args="suites/BasicIntegration.json /tmp/TR"
```

Perf Test
```
./gradlew  runTest --args="suites/BasicPerf.json /tmp/TR"
```

The default server that the test will run against is specified in the test config file.
To override the default server, set an environment variable as in the following example.
```
export TEST_RUNNER_SERVER_SPECIFICATION_FILE="workspace-dev.json" 
./gradlew  runTest --args="configs/integration/BasicAuthenticated.json /tmp/TR"
```

#### SA keys from Vault
Each service account JSON files in the resources/serviceaccounts directory of this project specifies a default file
path for the client secret file. This default path should match where the render-config.sh script puts the secret.

#### Use a local Workspace Manager client JAR file
The version of the Workspace Manager client JAR file is specified in the build.gradle file in this sub-project. This JAR file is
fetched from the Broad Institute Maven repository. You can override this to use a local version of the Workspace Manager client
JAR file by specifying a Gradle project property, either with a command line argument

`./gradlew -Pworkspacemanagerclientjar=~/terra-workspace-manager/workspace-manager-client/build/libs/workspace-manager-client-0.5.0-SNAPSHOT.jar runTest --args="configs/integration/BasicAuthenticated.json"

or an environment variable.

```
export ORG_GRADLE_PROJECT_workspacemanagerclientjar=~/terra-workspace-manager/workspace-manager-client/build/libs/workspace-manager-client-0.5.0-SNAPSHOT.jar
./gradlew runTest --args="configs/integration/BasicAuthenticated.json /tmp/TestRunnerResults"
```

This is useful for debugging or testing local server code changes that affect the generated client library (e.g. new API
endpoint). You can generate the Workspace Manager client library with the Gradle assemble task of the workspace-manager-client sub-project.

```
cd ~/terra-workspace-manager/workspace-manager-client
../gradlew clean assemble
ls -la ./build/libs/*jar
```