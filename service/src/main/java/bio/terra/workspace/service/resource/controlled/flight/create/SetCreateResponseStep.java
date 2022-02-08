package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceType;

public class SetCreateResponseStep implements Step {
  private final ControlledResource resource;

  public SetCreateResponseStep(ControlledResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    // Return the input resource as the response; it is filled in properly on the request,
    // so has all the right data except the private resource state, if relevant. And if we have made
    // it this far, we have made the request contents true.
    FlightMap workingMap = flightContext.getWorkingMap();
    ControlledResource responseResource;
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      switch (resource.getResourceType()) {
        case CONTROLLED_GCP_GCS_BUCKET:
          {
            ControlledGcsBucketResource controlledResource =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
            responseResource =
                controlledResource.toBuilder()
                    .privateResourceState(PrivateResourceState.ACTIVE)
                    .build();
            break;
          }
        case CONTROLLED_GCP_BIG_QUERY_DATASET:
          {
            ControlledBigQueryDatasetResource controlledResource =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
            responseResource =
                controlledResource.toBuilder()
                    .privateResourceState(PrivateResourceState.ACTIVE)
                    .build();
            break;
          }
        case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE:
          {
            ControlledAiNotebookInstanceResource controlledResource =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
            responseResource =
                controlledResource.toBuilder()
                    .privateResourceState(PrivateResourceState.ACTIVE)
                    .build();
            break;
          }
        default:
          throw new InvalidMetadataException(
              "Unknown controlled resource type " + resource.getResourceType());
      }
    } else {
      responseResource = resource;
    }
    workingMap.put(JobMapKeys.RESPONSE.getKeyName(), responseResource);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
