package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_LOCATION;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class UpdateNotebookResourceLocationAttributesStep implements Step {

  private final ControlledAiNotebookInstanceResource resource;
  private final ResourceDao resourceDao;

  public UpdateNotebookResourceLocationAttributesStep(
      ControlledAiNotebookInstanceResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String previousAttributes = resource.attributesToJson();
    flightContext.getWorkingMap().put(ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    String requestedLocation =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_NOTEBOOK_LOCATION, String.class);
    String newAttributes =
        DbSerDes.toJson(
            new ControlledAiNotebookInstanceAttributes(
                resource.getInstanceId(), requestedLocation, resource.getProjectId()));

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
