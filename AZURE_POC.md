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
./gradlew :service:bootRun --args='--spring.profiles.active=azure'
``` 
or by setting the envvar:
```shell script
export SPRING_PROFILES_ACTIVE='azure'
```

An alternative is to use `SPRING_PROFILES_INCLUDE` to include the profile without overriding
other profiles that have been set. For example, my personal setup is:
```shell script
export SPRING_PROFILES_INCLUDE=dd,azure,human-readable-logging
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
