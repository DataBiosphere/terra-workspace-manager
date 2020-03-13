# kernel-service-poc
This repository started as a proof-of-concept. It is expanding to serve as a template
project for MC Terra services.

In general, once you copy this project, you can search for the word "template" and
fill in your service or some other appropriate name.

[Deliverybot Dashboard](https://app.deliverybot.dev/DataBiosphere/framework-version/branch/master)

## OpenAPI V3 - formerly swagger
The template provides a simple OpenAPI V3 yaml document that includes a /status
endpoint and a /api/template/v1/ping endpoint. The ping endpoint is there to
show the full plumbing for an endpoint that uses the common exception handler to 
return an ErrorReport.

The OpenAPI document also contains two components that we would like to have common
across all of our APIs:
<ul>
<li>ErrorReport - a common error return structure</li>
<li>SystemStatus - a common response to the /status endpoint</li>
</ul>

A swagger-ui page is available at /api/swagger-ui.html on any running instance. 
TEMPLATE: Once a service has a stable dev/alpha instance, a link to its 
swagger-ui page should go here.

## Spring Boot
We use Spring Boot as our framework for REST servers. The objective is to use a minimal set
of Spring features; there are many ways to do the same thing and we would like to constrain ourselves
to a common set of techniques.

### Configuration
We only use Java configuration. We never use XML files.

In general, we use type-safe configuration parameters as shown here: 
[Type-safe Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-typesafe-configuration-properties).
That allows proper typing of parameters read from property files or environment variables. Parameters are
then accessed with normal accessor methods. You should never need to use an `@Value` annotation.

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
<ul>
<li><code>@Component</code> Regular singleton class, like a service.</li>
<li><code>@Repository</code> DAO component</li>
<li><code>@Controller</code> REST Controller</li>
<li><code>@Configuration</code> Definition of properties</li>
</ul>

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
```
@Component
public class Foo {
    private Bar bar;
    private Fribble fribble;

    @Autowired
    public Foo(Bar bar, Fribble fribble) {
        this.bar = bar;
        this.foo = foo;
    }
```
Spring will pass in the instances of Bar and Fribble into the constructor.
It is possible to autowire a specific class member, but that is rarely necessary:
```
@Component
public class Foo {
    @Autowired
    private Bar bar;
```

#### REST Annotations
<ul>
<li><code>@RequestBody</code> Marks the controller input parameter receiving the body of the request</li>
<li><code>@PathVariable("x")</code> Marks the controller input parameter receiving the parameter <code>x</code></li>
<li><code>@RequestParam("y")</code> Marks the controller input parameter receiving the query parameter<code>y</code></li>
</ul>

#### JSON Annotations
We use the Jackson JSON library for serializing objects to and from JSON. Most of the time, you don't need to 
use JSON annotations. It is sufficient to provide setter/getter methods for class members
and let Jackson figure things out with interospection. There are cases where it needs help
and you have to be specific.

The common JSON annotations are:
<ul>
<li><code>@JsonValue</code> Marks a class member as data that should be (de)serialized to(from) JSON.
You can specify a name as a parameter to specify the JSON name for the member.</li>
<li><code>@JsonIgnore</code>  Marks a class member that should not be (de)serialized</li>
<li><code>@JsonCreator</code> Marks a constructor to be used to create an object from JSON.</li>
</ul>

For more details see [Jackson JSON Documentation](https://github.com/FasterXML/jackson-docs)

## Main Code Structure
This section explains the code structure of the template. Here is the directory structure:
```
/src
  /main
    /java
      /bio/terra/TEMPLATE
        /app
          /configuration
          /controller
        /common
          /exception
        /service
          /ping
    /resources
```
<ul>
<li><code>/app</code> For the top of the application, including Main and the StartupInitializer</li>
<li><code>/app/configuration</code> For all of the bean and property definitions</li>
<li><code>/app/controller</code> For the REST controllers. The controllers typically do very little.
They invoke a service to do the work and package the service output into the response. The
controller package also defines the global exception handling.</li>
<li><code>/common</code> For common models and common exceptions; for example, a model that is 
shared by more than one service.</li>
<li><code>/common/exception</code> The template provides abstract base classes for the commonly
used HTTP status responses. They are all based on the ErrorReportException that provides the
explicit HTTP status and "causes" information for our standard ErrorReport model.</li>
<li><code>/service</code> Each service gets a package within. We handle cloud-platform specializations
within each service.</li>
<li><code>/service/ping</code> The example service; please delete.</li>
<li><code>/resources</code> Properties definitions, database schema definitions, and the REST API definition</li>
</ul>

## Test Structure
There are sample tests for the ping service to illustrate two styles of unit testing.

## Deployment
### On commit to master
1. New commit is merged to master
2. [The master_push workflow](https://github.com/DataBiosphere/kernel-service-poc/blob/gm-deployment/.github/workflows/master_push.yml) is triggered. It builds the image, tags the image & commit, and pushes the image to GCR. It then sends a [dispatch](https://help.github.com/en/actions/reference/events-that-trigger-workflows#external-events-repository_dispatch) with the new version for the service to the [framework-version repo](https://github.com/DataBiosphere/framework-version).
3. This triggers the [update workflow](https://github.com/DataBiosphere/framework-version/blob/master/.github/workflows/update.yml), which updates the JSON that maps services to versions to map to the new version for the service whose repo sent the dispatch. The JSON is then committed and pushed.
4. This triggers the [tag workflow](https://github.com/DataBiosphere/framework-version/blob/master/.github/workflows/tag.yml), which tags the new commit in the framework-version repo with a bumped semantic version, yielding a new version of the whole stack incorporating the newly available version of the service.
5. The new commit corresponding to the above version of the stack is now visible on the [deliverybot dashboard](https://app.deliverybot.dev/DataBiosphere/framework-version/branch/master). It can now be manually selected for deployment to an environment.
6. Deploying a version of the stack to an environment from the dashboard triggers the [deploy workflow](https://github.com/DataBiosphere/framework-version/blob/master/.github/workflows/deploy.yml). This sends a dispatch to the [framework-env repo](https://github.com/DataBiosphere/framework-env) with the version that the chosen commit is tagged with, and the desired environment.
7. The dispatch triggers the [update workflow in that repo](https://github.com/DataBiosphere/framework-env/blob/master/.github/workflows/update.yml), which similarly to the one in the framework-version one, updates a JSON. This JSON maps environments to versions of the stack. It is updated to reflect the desired deployment of the new stack version to the specified environment and the change is pushed up.
8. The change to the JSON triggers the [apply workflow](https://github.com/DataBiosphere/framework-env/blob/master/.github/workflows/apply.yml), which actually deploys the desired resources to k8s. It determines the services that must be updated by diffing the stack versions that the environment in question is transitioning between and re-deploys the services that need updates.
