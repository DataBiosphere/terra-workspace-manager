# terra-workspace-manager
This repository holds the MC Terra Workspace Manager (WSM) service, client, and
integration test projects.

This readme provides general information about WSM. Specifics about how to do development
within the Broad Institute's CI/CD system can be found in [DEVELOPMENT](DEVELOPMENT.md).

## Overview

WSM provides workspaces; contexts for holding the work of
individuals and teams. A _workspace_ has members that are granted some role on the
workspace (OWNER, READER, WRITER). The members can create and manage _resources_ in the
workspace. There are two types of resources:
- _controlled resources_ are cloud resources (e.g., buckets) whose attributes,
  permissions, and lifecycle are controlled by the Workspace Manager. Controlled resources
  are created and managed using Workspace Manager APIs.
- _referenced resources_ are cloud resources that are independent of the
  Workspace Manager. A workspace may hold a reference to such a resource. The Workspace
  Manager has no role in managing the resource’s lifecycle or attributes.

Resources have unique names within the workspace, allowing users of the workspace to
locate and refer to them in a consistent way, whether they are controlled or referenced.

The resources in a workspace may reside on different clouds. Users may create one _cloud
context_ for each cloud platform where they have controlled or referenced resources.

Workspace Manager provides the minimum interface to allow it to control permissions and
lifecycle of controlled resources. All other access, in particular data reading and
writing, are done using the native cloud APIs.

Controlled resources may be _shared_ or _private_. Shared resources are accessible to
workspace members with their workspace role. That is, if you have READER on the workspace,
then you can read the resource (however that is defined for the specific resource); if you
have WRITER on the workspace, then you can write the resource.

Private resources are available to a single member of the workspace. At the present time,
a private resource is available only to its creator.

WSM has latent support for _applications_. No applications exist at this time. The concept
is that an application is a distinguished service accounts. Owners of the workspace can
control which applications are allowed access to the workspace. If an application is given
access, then it can create application-owned resources. The goal is to allow applications
to create constellations of resources that support the application, and not let them be
messed with by workspace READERS and WRITERS.

## WSM Client
Workspace Manager publishes an API client library generated from its OpenAPI Spec v3
interface definition.

### Usage (Gradle)

Include the Broad Artifactory repositories:
```gradle
repositories {
    maven {
        url "https://broadinstitute.jfrog.io/broadinstitute/libs-snapshot-local/"
    }
}
```

Add a dependency like
```gradle
implementation(group: 'bio.terra', name: 'workspace-manager-client', version: 'x.x.x')
```
See [settings.gradle](settings.gradle) for the latest version information.

## Build Structure

We use [gradle](https://gradle.org/) as our build tool. The repository is organized as a
composite build, with common build logic pulled into [convention plugins](https://docs.gradle.org/current/samples/sample_convention_plugins.html).
There are three mostly independent projects:
- _service_ - the Workspace Manager Service
- _client_ - the OpenAPI-generated client
- _integration_ - the TestRunner-based integration test project

The build structure is:
```
terra-workspace-manager
  |
  + settings.gradle
  + build.gradle
  |
  +-- buildSrc/src/main/groovy (convention plugins)
  |    |
  |    + terra-workspace-manager.java-conventions.gradle
  |    + terra-workspace-manager.library-conventions.gradle
  |
  +-- service
  |    |
  |    + build.gradle (service build; test dependency on client)
  |
  +–- client
  |    |
  |    + build.gradle
  |
  +-- integration (formerly clienttest)
       |
       + build.gradle (dependency on client)
```

This build, and others in MC Terra require access to the Broad Institute's
Artifactory server. That is where supporting libraries are published and where we publish
the WSM client

### Dependencies
We use [Gradle's dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
to ensure that builds use the same transitive dependencies, so they're reproducible. This means that
adding or updating a dependency requires telling Gradle to save the change.

Each WSM project has separate dependency lock state.  If you're getting errors
that mention "dependency lock state" after changing a build file, you will need to one of
these commands:

```sh
./gradlew :service:dependencies --write-locks
./gradlew :client:dependencies --write-locks
./gradlew :integration:dependencies --write-locks
```

## Workspace Manager Service
The bulk of the code is in the `service` project. This section describes that projet.

### Spring Boot
The service project uses Spring Boot as the framework for REST servers. The objective is to use a minimal set
of Spring features; there are many ways to do the same thing and we would like to constrain ourselves
to a common set of techniques.

#### Configuration
We only use YAML configuration. We never use XML or .properties files.

In general, we use type-safe configuration parameters as shown here: 
[Type-safe Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties).
That allows proper typing of parameters read from property files or environment variables. Parameters are
then accessed with normal accessor methods. You should never need to use an `@Value` annotation.

Be aware that environment variables will override values in our YAML configuration.
This should not be used for configuration as it makes the source of values harder to track,
but it may be useful for debugging unexpected configurations. See Spring Boot's 
[Externalized Configuration documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#boot-features-external-config)
for the exact priority order of configurations.

#### Initialization
When the applications starts, Spring wires up the components based on the profiles in place.
Setting different profiles allows different components to be included. This technique is used
as the way to choose the cloud platform (Google, Azure, AWS) code to include.

We use the Spring idiom of the `postSetupInitialization`, found in ApplicationConfiguration.java,
to perform initialization of the application between the point of having the entire application initialized and
the point of opening the port to start accepting REST requests.

#### Annotating Singletons	
The typical pattern when using Spring is to make singleton classes for each service, controller, and DAO.	
You do not have to write the class with its own singleton support. Instead, annotate the class with	
the appropriate Spring annotation. Here are ones we use:

- `@Component` Regular singleton class, like a service.
- `@Repository` DAO component
- `@Controller` REST Controller
- `@Configuration` Definition of properties

#### Common Annotations
There are other annotations that are handy to know about.

Use `@Nullable` to mark method interface and return parameters that can be null.

##### Autowiring
Spring wires up the singletons and other beans when the application is launched.
That allows us to use Spring profiles to control the collection of code that is
run for different environments. Perhaps obviously, you can only autowire singletons to each other. You cannot autowire
dynamically created objects.

There are two styles for declaring autowiring.
The preferred method of autowiring, is to put the annotation on the constructor
of the class. Spring will autowire all of the inputs to the constructor.

```java
@Component
public class Foo {
    private final Bar bar;
    private Fribble fribble;

    @Autowired
    public Foo(Bar bar, Fribble fribble) {
        this.bar = bar;
        this.foo = foo;
    }
}
```

Spring will pass in the instances of Bar and Fribble into the constructor.
It is possible to autowire a specific class member, but that is rarely necessary:

```java
@Component
public class Foo {
    @Autowired
    private Bar bar;
}
```

##### REST Annotations
- `@RequestBody` Marks the controller input parameter receiving the body of the request
- `@PathVariable("x")` Marks the controller input parameter receiving the parameter `x`
- `@RequestParam("y")` Marks the controller input parameter receiving the query parameter`y`


##### JSON Annotations
We use the Jackson JSON library for serializing objects to and from JSON. Most of the time, you don't need to 
use JSON annotations. It is sufficient to provide setter/getter methods for class members
and let Jackson figure things out with interospection. There are cases where it needs help
and you have to be specific.

The common JSON annotations are:

- `@JsonValue` Marks a class member as data that should be (de)serialized to(from) JSON.
  You can specify a name as a parameter to specify the JSON name for the member.
- `@JsonIgnore`  Marks a class member that should not be (de)serialized
- `@JsonCreator` Marks a constructor to be used to create an object from JSON.

For more details see [Jackson JSON Documentation](https://github.com/FasterXML/jackson-docs)


### Service Code Structure
This section explains the code structure of the template. Here is the directory structure:

```
src/main/
  java/
    bio/terra/workspace/
      app/
        configuration/
        controller/
      common/
        exception/
        utils/
      db/
        exception/
        model/
      service/
        buffer/
        crl/
        datarepo/
        iam/
        job/
        resource/
        spendprofile/
        stage/
        status/
        workspace/
  resources/
```
- `app/` For the top of the application, including Main and the StartupInitializer
- `app/configuration/` For all of the bean and property definitions
- `app/controller/` For the REST controllers. The controllers typically do very little.
They perform access checks and validate input, invoke a service to do the work, and package the service output into the response. The
controller package also defines the global exception handling.
- `common/` For common models, exceptions, and utilities.
shared by more than one service.
- `common/exception/` A set of common abstract base classes that support the ErrorReport REST API
return structure live in the [Terra Common Library ](https://github.com/DataBiosphere/terra-common-lib).
All WSM exceptions derive from those. Exceptions common across services live here.
- `service/` Each service gets a package within. We handle cloud-platform specializations
within each service.
- `service/buffer/` Thin interface to access the
[Resource Buffer Service](https://github.com/DataBiosphere/terra-resource-buffer)
for allocating GCP projects: the cloud context for Google cloud.
- `service/crl/` Thin interface to access the
[Terra Cloud Resource Library](https://github.com/DataBiosphere/terra-cloud-resource-lib)
used for allocating cloud resources.
- `service/datarepo` Thin interface to access the
[Terra Data Repository](https://github.com/DataBiosphere/jade-data-repo) for making
_referenced resources_ pointing to TDR snapshots.
- `service/iam` Methods for accessing [Sam](https://github.com/broadinstitute/sam) for
authorization definition and checking. This service provides retries and specific methods
for the WSM operations on Sam.
- `service/job` Methods for launching Stairway flights, waiting on completion, and getting
flight results
- `service/resource` One of the main services in WSM. Manages controlled and referenced resources.
- `service/spendprofile` Temporary methods to use fake spend profiles. Eventually, it will
become a thin layer accessing the Spend Profile Manager when that arrives.
- `service/stage` Feature locking service
- `service/status` Implementation of the /status endpoint
- `service/workspace` The other main service in WSM. Manages CRUD for workspaces and cloud
contexts.
- `resources/` Properties definitions, database schema definitions, and the REST API definition

### Service Test Structure
There are three groups of tests.

#### Unit Tests
The unit tests are written using JUnit. The implementations are in
`src/test/java/bio/terra/workspace/`. Unit tests derive from `common/BaseUnitTest.java`.
Some unit tests depend on the availability of a running Postgresql server.

#### Connected Tests
The connected tests are also written using JUnit.
The implementations are mixed in with the unit tests in
`src/test/java/bio/terra/workspace/`. Connected tests derive from `common/BaseConnectedTest.java`.
Connected tests depend on the availability of a running Postgresql server. They also rely
on a populated "config" directory containing service accounts and keys that allows the tests
to use dependent services such as Sam, Buffer, and TDR. The config collecting process relies on
secrets maintained in Vault in the Broad Institute environment.

In general, developers writing new endpoints should add MockMVC-based unit or
connected tests to test their code (example: [WorkspaceApiControllerTest](service/src/test/java/bio/terra/workspace/app/configuration/external/controller/WorkspaceApiControllerTest.java)).
These tests let us act as if we're making HTTP calls against a local server
and validate the full request lifecycle through all
the [layers of WSM](DEVELOPMENT.md#Layering), whereas the previous style of
service-only tests did not cover code in the controller layer.

#### Integration Tests
Integration testing is done using
[Test Runner](https://github.com/DataBiosphere/terra-test-runner).
The integration tests live in the `integration` project. Consult the integration
[README](integration/README.md) for more details.

In the early days of the project, there were JUnit-based integration tests. We are in
process of migrating them to Test Runner.

#### Cleaning up workspaces in tests

We have 2 ways of cleaning up resources (WSM workspace, SAM workspace, GCP project):

1. Connected tests use Janitor. Janitor deletes GCP project and not SAM
   workspace (see
   [here](https://github.com/DataBiosphere/terra-workspace-manager/pull/755#discussion_r942717257) for details).
2. Tests call WSM `deleteWorkspace()`. This deletes WSM workspace + SAM workspace +
   GCP project.

Connected tests that use mock SamService: Tests don't need to call
`deleteWorkspace()` because there is no SAM workspace to clean up.

Connected tests that use real SamService: Tests should call `deleteWorkspace()`
to clean up SAM workspaces. Why not just call `deleteWorkspace()` and not use
janitor? Janitor is useful in case test fails (or `deleteWorkspace()` fails).

Integration tests: Tests should call `deleteWorkspace()` because integration
tests don't use janitor. Most tests don't need to worry about this because
`WorkspaceAllocateTestScriptBase.java` deletes the workspace it creates.
