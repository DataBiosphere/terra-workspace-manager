package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_LOCATION;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceAttributes;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceAttributes;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class UpdateInstanceResourceLocationAttributesStep implements Step {

  private final ControlledResource resource;
  private final ResourceDao resourceDao;

  public UpdateInstanceResourceLocationAttributesStep(
      ControlledResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String previousAttributes = resource.attributesToJson();
    flightContext.getWorkingMap().put(ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    String location =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_LOCATION, String.class);

    String newAttributes =
        DbSerDes.toJson(
            switch (resource.getResourceType()) {
              case CONTROLLED_GCP_GCE_INSTANCE -> {
                ControlledGceInstanceResource gceInstance =
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
                yield new ControlledGceInstanceAttributes(
                    gceInstance.getInstanceId(), location, gceInstance.getProjectId());
              }
              case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> {
                ControlledAiNotebookInstanceResource aiNotebookInstance =
                    resource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
                yield new ControlledAiNotebookInstanceAttributes(
                    aiNotebookInstance.getInstanceId(),
                    location,
                    aiNotebookInstance.getProjectId());
              }
              default -> throw new InternalLogicException("Bad resource type passed to step.");
            });

    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), null, null, newAttributes, null);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String previousAttributes =
        flightContext.getWorkingMap().get(ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), null, null, previousAttributes, null);
    return StepResult.getStepResultSuccess();
  }
}
