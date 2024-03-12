# Adding Resources to Workspace Manager

One of the most frequent WSM tasks is to add a new resource. I have tried to make this as
simple as practical. I have also tried to allow parallel resource development with a
minimum of file conflicts.

## Checklist

This section summarizes the steps to add a new resource. I recommend this order to be able
to compile your code as you go.

1. Create your resource type and attributes in the `openapi` subproject.
   - Add your resource file to `src/resources/` directory, defining the resource and the
     attributes objects.
   - Edit the `resource_type.yaml` file in that directory, creating an enumeration for the
     resource and adding the appropriate resource element to the union objects.
   - Run `./gradlew :openapi:buildOpenApi` to make sure your syntax and references are correct.
2. Create your Java resource package in `service/src/main/java/bio.terra/workspace/service/resources/`
      in the appropriate package
    - `controlled/{cloud}/{your resource}`
    - `referenced/{cloud}/{your resource}`
3. Create classes for your resource
   - Attributes class describing the fields beyond the common metadata
   - Resource class. This is the most complex class to implement and supplies the bulk of
     your resource code
   - Handler class
4. If you are making a controlled resource you will also need to add:
   - Step classes as required for object create and update. These will be needed to
     complete the resource class.
   - Handler class. Used primarily to give the `ResourceDao` a way to create resource
     objects by type.
5. Add your new resource type to `resource/model/WsmResourceFamily`,
   `resource/model/WsmResourceType` and `ApiResourceType`. At this point,
   `./gradlew :service:compileJava` should succeed.
6. Add your REST API
   - In the `openapi` subproject, in the `parts` directory, create a yaml file for your
     resource API. The file should contain all resource-type-specific parameters,
     responses, and schemas as well as the endpoint paths. You can find shared components
     in the `src/common` directory.

       **NOTE:** *At this point in time, there is too much variation in the different
       resource APIs. Please follow the pattern in the* `??_gcp_big_query_dataset`
       *interface.*
7. Edit the appropriate controller to implement your API. Depending on the implementation
   details, you may need to add methods to the `ControlledResourceService` or the
   `ReferencedResourceService`.
8. Tests
   - Unit tests: When you don't need to talk to real cloud. Example: Search for `ControlledBqDataset` in   
     ([`ControlledGcpResourceApiControllerTest.java`](https://github.com/DataBiosphere/terra-workspace-manager/blob/main/service/src/test/java/bio/terra/workspace/app/controller/ControlledGcpResourceApiControllerTest.java))
   - Connected tests: If you need to talk to real cloud, for example to create your
     resource. The majority of tests should be connected tests. Each resource is in a
     different test file. Example:
     ([`ControlledGcpResourceApiControllerBqDatasetTest.java`](https://github.com/DataBiosphere/terra-workspace-manager/blob/main/service/src/test/java/bio/terra/workspace/app/controller/ControlledGcpResourceApiControllerBqDatasetTest.java))
       - Controller tests are preferred over service tests, since former also tests controller layer.
   - Integration test: Tests using TestRunner framework. If your resource supports cloning, add your resource to
     `CloneWorkspace` integration test.


## More Information

### Asynchronous Interfaces

We have a strong bias for synchronous APIs. If you have an operation that runs over the
network time more that 10% of the time, it is appropriate to make it an asynchronous
interface. See
[MC Terra - Async REST API Specification](https://docs.google.com/document/d/1PTd4xvmV9xnEkWaIgFc6d3VUyJwwymQTFdhpKrdsUKw/edit#heading=h.ol9mx3vfhjjj)
for details about how async interfaces are provided in MC Terra.

### Writing Flight Steps

When writing steps for a Stairway flight, you need to make your code idempotent. It needs
to be able to be restarted in the event of pod failures and rolling upgrades. See 
[Stairway Github Repo](https://github.com/DataBiosphere/stairway) for further documentation.
In particular, the [Flight Developer Guide](https://github.com/DataBiosphere/stairway/blob/develop/FLIGHT_DEVELOPER_GUIDE.md).

### Files for Resources

This section itemizes the files you will probably need to create or edit to add your resource.

* {stewardship} is either `controlled` or `referenced` as defined in StewardshipType.java
* {cloud} is either `gcp`, `azure`, or `any` as defined in CloudPlatform.java
* {resource} is the name of your resource; for example, `BigQueryDataset`

#### Resource-Specific Files

Most of the code you make is in files specific to the new resource. These are:

| Project | Directory Path | File | Notes |
| ------- | -------------- | ---- | ----- |
| integration | src/main/java/scripts/testscripts | `{resource}Lifecycle.java` |  |
| integration | src/main/resources/configs/integration | `{resource}Lifecycle.json` |  |
| openapi | src/parts | `{stewardship}_{cloud}_{resource}.yaml` | |
| openapi | src/resources | `{cloud}_{resource}.yaml` |  |
| service | src/main/java/.../service/resource/{stewardship}/cloud/{cloud}/{resource} | `{resource}Attributes.java` | |
| service | src/main/java/.../service/resource/{stewardship}/cloud/{cloud}/{resource} | `{resource}Handler.java` | controlled only |
| service | src/main/java/.../service/resource/{stewardship}/cloud/{cloud}/{resource} | `{resource}Resource.java` |
| service | src/main/java/.../service/resource/{stewardship}/cloud/{cloud}/{resource} | `*Steps.java` | Create and update step files; controlled only |
| service | src/test/java/.../service/resource/{stewardship}/cloud/{cloud}/{resource} | `*Test.java` | Unit and connected tests |

#### Shared Files

| Project | Directory Path | File | Notes |
| ------- | -------------- | ---- | ----- |
| integration | src/main/resources/suites | FullIntegration.json | If your test should run as part of the automated integration tests |
| openapi | src/resources | resource_type.yaml | |
| service | src/main/java/.../app/controller | `{stewardship}{cloud}Controller.java` | |
| service | src/main/java/.../service/resource/{stewardship} | `{stewardship}ResourceService.java` | If the common paths are not sufficient |
| service | src/main/java/.../service/resource/model | `WsmResource.java` | `enum` of all specific resources |
| service | src/main/java/.../service/resource/model | `WsmResourceFamily.java` | `enum` of kinds of resources |

### Cloud Resource Library (CRL)

All cloud resource allocation in WSM should be done using CRL. CRL provides a common point
of logging and, in the fullness of time, perhaps tagging resources. It is out of scope for
this document to describe adding things to CRL. See
[Common Resource Library Github](https://github.com/DataBiosphere/terra-cloud-resource-lib)
for information.

Adding resource allocation to CRL sometimes requires adding resource cleanup to the Janitor
service. We use the Janitor service in development to clean up test resources. That way,
tests can crash and burn and we are still able to clean up. See
[Janitor Github](https://github.com/DataBiosphere/terra-resource-janitor) for more information.

### GCP Specifics

When adding a controlled resource in GCP, there are often other tasks that need to be
done.

#### Custom Roles

Typically, the built-in GCP roles for objects do not work well for WSM. WSM needs to be in
sole control of create, delete, and IAM. Often, roles that allow attribute update also
allow those actions. The solution is to create custom roles.

The code structure for adding these roles is in `resource/controlled/cloud/gcp` in the
files `CustomGcpIamRole` and `CustomGcpIamRoleMapping`.

#### Enabling APIs

Enabling resources can take a while so we do it in the Resource Buffer Service (RBS). RBS
manages pools of pre-configured projects. When a GCP cloud context is created, we request
a project from a particular pool in RBS.

If you need to enable a new API to support your new GCP resource, it needs to be done in
RBS. See
[Resource Buffer Service Github](https://github.com/DataBiosphere/terra-resource-buffer)
for more details.

### Azure Specifics

When deleting an Azure resource, `DeleteAzureControlledResourceStep.java` contains a base class for the deletion steps
that contains handling for common errors that happen when deleting Azure resources. 

**TBS**
