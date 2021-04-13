package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.CreateServiceAccountStep;
import bio.terra.workspace.service.resource.controlled.flight.create.notebook.GenerateServiceAccountIdStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  public CreateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    // store the resource metadata in the WSM database
    addStep(new StoreMetadataStep(flightBeanBag.getResourceDao()));

    // create the cloud resource via CRL
    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    // Stairway does not provide a way to specify parameterized types for deserialization
    @SuppressWarnings("unchecked")
    final List<ControlledResourceIamRole> privateResourceIamRole =
        inputParameters.get(ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLES, List.class);

    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new CreateGcsBucketStep(
                flightBeanBag.getCrlService(),
                resource.castToGcsBucketResource(),
                flightBeanBag.getWorkspaceService()));
        break;
      case BIG_QUERY_DATASET:
        break;
      case AI_NOTEBOOK_INSTANCE:
        addStep(new GenerateServiceAccountIdStep());
        addStep(
            new CreateServiceAccountStep(
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService(),
                resource.castToAiNotebookInstanceResource()));
        addStep(
            new CreateAiNotebookInstanceStep(
                flightBeanBag.getCrlService(),
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getWorkspaceService()));
        // TODO(PF-469): Set permissions.
        break;
      default:
        throw new IllegalStateException(
            String.format("Unrecognized resource type %s", resource.getResourceType()));
    }
    // create the Sam resource associated with the resource
    addStep(
        new CreateSamResourceStep(
            flightBeanBag.getSamService(), resource, privateResourceIamRole, userRequest));

    // assign custom roles to the resource based on Sam policies
    // TODO: can this step be the same for all resource types?

    // Populate the return response
    addStep(new SetCreateResponseStep(resource));
  }
}
