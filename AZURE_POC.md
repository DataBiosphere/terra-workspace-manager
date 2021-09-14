# Azure PoC

This file describes the Azure PoC branch. The purpose of the PoC is to prototype how workspace
manager might work on Azure. The goals and approach are described in
[WSM for Azure PoC](https://docs.google.com/document/d/1N3FlbnLw8LotLQ4bKRB8vEkJlbAF3evuz8bK9ZV-PUg/edit#heading=h.tg5lhyvrdvny).

## azure-poc branch
The azure-poc branch holds the integrated code that for running the PoC. The expectation is that
developers will branch off of the azure-poc branch and will make PRs to merge onto the branch. As
this is a PoC, we will not require code reviews. Merged code should compile and pass the WSM unit
and connected tests. It should also pass whatever azure-poc tests we have deployed at the time.

Ideally, we will be able to pull from the`main` branch into the `azure-poc` branch to keep it
current with ongoing WSM feature development.

## Enabling the PoC Features
We use the `azure` profile to enable the PoC features. That can be enabled on the gradle
command line like this:
```text
./gradlew :service:bootRun --args='--spring.profiles.active=azure,human-readable-logging'
``` 
or by setting the envvar:
```shell script
export SPRING_PROFILES_ACTIVE='azure'
```

## Feature Control
You can conditionally include Azure features by autowiring the `AzureState` component
and then testing its `isEnabled()` method. The boolean response is determined based on
setting the `azure` profile. For example,
```java
public class Foo {
  private final AzureState azureState;


  // This is the preferred constructor form of autowiring
  @Autowired
  public Foo(AzureState azureState) {
    this.azureState = azureState;
  }

  public void doSomething() {
    // Common idiom for checking if we are running the Azure Poc
    if (azureState.isEnabled()) {
      // do azure stuff
    } else {
      // do existing stuff
    }
  }
}
```

## Testing

### Running Tests
You can run the azure-specific tests like this:
```shell script
./gradlew :service:azureTest
```

### Developing Tests
To add a test that will run as part of the `azureTest` gradle task, simply derive your
test from `BaseAzureTest`.

## Authentication
I have the authentication setup so that OIDC and Bearer authentication use the existing
authentication/authorization pat and use Sam. Basic
authentication uses mock Sam. For Azure PoC, you must **only** use Basic authentication to
get consistent results.

I set it up this way so that existing tests will continue to run and give us some assurance
that we have not broken things too badly.

## Mock Sam

### Database
Two PoC tables in the WSM database:
* `poc_user` holds the user's Azure objectId and the user's email
* `poc_grant` has a row for each grant to a workspace: workspace id, role, user id

All access checking will be based on lookups of workspace roles stored in the `poc_grant` table.
 

