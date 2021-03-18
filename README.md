# terra-workspace-manager
This repository holds the Workspace Manager service, the MC-Terra component
responsible for managing resources and the applications/resources they use.

To push versions of this repository to different environments (including 
per-developer integration environments), update the [terra-helmfile deployment definitions](https://github.com/broadinstitute/terra-helmfile/pull/13).

## OpenAPI V3 - formerly swagger
A swagger-ui page is available at /swagger-ui.html on any running instance. For existing instances, those are:

- dev: https://workspace.dsde-dev.broadinstitute.org/swagger-ui.html
- alpha: https://workspace.dsde-alpha.broadinstitute.org/swagger-ui.html
- staging: https://workspace.dsde-staging.broadinstitute.org/swagger-ui.html
- perf: https://workspace.dsde-perf.broadinstitute.org/swagger-ui.html
- prod: https://workspace.dsde-prod.broadinstitute.org/swagger-ui.html 

We currently do not deploy to a production environment.

## Spring Boot
We use Spring Boot as our framework for REST servers. The objective is to use a minimal set
of Spring features; there are many ways to do the same thing and we would like to constrain ourselves
to a common set of techniques.

### Configuration
We only use YAML configuration. We never use XML or .properties files.

In general, we use type-safe configuration parameters as shown here: 
[Type-safe Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-typesafe-configuration-properties).
That allows proper typing of parameters read from property files or environment variables. Parameters are
then accessed with normal accessor methods. You should never need to use an `@Value` annotation.

Be aware that environment variables will override values in our YAML configuration.
This should not be used for configuration as it makes the source of values harder to track,
but it may be useful for debugging unexpected configurations. See Spring Boot's 
[Externalized Configuration documentation](https://docs.spring.io/spring-boot/docs/2.4.0/reference/html/spring-boot-features.html#boot-features-external-config)
for the exact priority order of configurations.

### Initialization
When the applications starts, Spring wires up the components based on the profiles in place.
Setting different profiles allows different components to be included. This technique is used
as the way to choose the cloud platform (Google, Azure, AWS) code to include.

We use the Spring idiom of the `postSetupInitialization`, found in ApplicationConfiguration.java,
to perform initialization of the application between the point of having the entire application initialized and
the point of opening the port to start accepting REST requests.

### Annotating Singletons	
The typical pattern when using Spring is to make singleton classes for each service, controller, and DAO.	
You do not have to write the class with its own singleton support. Instead, annotate the class with	
the appropriate Spring annotation. Here are ones we use:

- `@Component` Regular singleton class, like a service.
- `@Repository` DAO component
- `@Controller` REST Controller
- `@Configuration` Definition of properties

### Common Annotations
There are other annotations that are handy to know about.

#### Autowiring
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
```

Spring will pass in the instances of Bar and Fribble into the constructor.
It is possible to autowire a specific class member, but that is rarely necessary:

```java
@Component
public class Foo {
    @Autowired
    private Bar bar;
```

#### REST Annotations
- `@RequestBody` Marks the controller input parameter receiving the body of the request
- `@PathVariable("x")` Marks the controller input parameter receiving the parameter `x`
- `@RequestParam("y")` Marks the controller input parameter receiving the query parameter`y`


#### JSON Annotations
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

## Main Code Structure
This section explains the code structure of the template. Here is the directory structure:

```
src/main/
  java/
    bio/terra/TEMPLATE/
      app/
        configuration/
        controller/
      common/
        exception/
      service/
        ping/
  resources/
```
- `app/` For the top of the application, including Main and the StartupInitializer
- `app/configuration/` For all of the bean and property definitions
- `app/controller/` For the REST controllers. The controllers typically do very little.
They invoke a service to do the work and package the service output into the response. The
controller package also defines the global exception handling.
- `common/` For common models and common exceptions; for example, a model that is 
shared by more than one service.
- `common/exception/` The template provides abstract base classes for the commonly
used HTTP status responses. They are all based on the ErrorReportException that provides the
explicit HTTP status and "causes" information for our standard ErrorReport model.
- `service/` Each service gets a package within. We handle cloud-platform specializations
within each service.
- `service/ping/` The example service; please delete.
- `resources/` Properties definitions, database schema definitions, and the REST API definition

### Dependencies
We use [Gradle's dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
to ensure that builds use the same transitive dependencies, so they're reproducible. This means that
adding or updating a dependency requires telling Gradle to save the change. If you're getting errors
that mention "dependency lock state" after changing a dep, you need to do this step.

```sh
./gradlew dependencies --write-locks
``` 

## Test Structure
There are sample tests for the ping service to illustrate two styles of unit testing.

## Deployment
### On commit to dev
1. New commit is merged to dev
2. [The dev_push workflow](https://github.com/DataBiosphere/terra-workspace-manager/blob/dev/.github/workflows/dev_push.yml) is triggered. It builds the image, tags the image & commit, and pushes the image to GCR. It then sends a [dispatch](https://help.github.com/en/actions/reference/events-that-trigger-workflows#external-events-repository_dispatch) with the new version for the service to the [terra-helmfile repo](https://github.com/broadinstitute/terra-helmfile).
3. This updates the default [version mapping for the app in question](https://github.com/broadinstitute/terra-helmfile/blob/master/versions.yaml).
4. [Our deployment of ArgoCD](https://ap-argocd.dsp-devops.broadinstitute.org/applications) monitors the above repo, and any environments in which the app is set to auto-sync will immediately pick up the new version of the image. If the app is not set to auto-sync in an environment, it can be manually synced via the ArgoCD UI or API.

## Api Client
Workspace Manager publishes an API client library based on the OpenAPI Spec v3. 

### Usage (Gradle)
```gradle
implementation(group: 'bio.terra', name: 'terra-workspace-manager-client', version: '0.11.0-SNAPSHOT')
```

Note that the publishing of this artifact is currently manual. Whenever the OpenAPI definitions change,
we should publish a new version of this library to artifactory. Backwards compatible changes should
have a minor version bump, and breaking changes should have a major version bump. We will try to avoid
breaking changes at all costs.

### Publishing

To publish, you will need to export the `ARTIFACTORY_USERNAME` and `ARTIFACTORY_PASSWORD` environment variables for the Broad artifactory. To build and publish:

```sh
./gradlew workspace-manager-client:artifactoryPublish
```
