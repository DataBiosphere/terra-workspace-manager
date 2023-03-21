package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_LOCATION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class UpdateNotebookResourceAttributesStep implements Step {

  private final ControlledAiNotebookInstanceResource resource;
  private final ResourceDao resourceDao;

  public UpdateNotebookResourceAttributesStep(
      ControlledAiNotebookInstanceResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String previousAttributes = resource.attributesToJson();
    flightContext.getWorkingMap().put(ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        FlightUtils.getRequired(
            flightContext.getInputParameters(),
            CREATE_NOTEBOOK_PARAMETERS,
            ApiGcpAiNotebookInstanceCreationParameters.class);

    String creationMachineType = creationParameters.getMachineType();

    AcceleratorConfig creationAcceleratorConfig =
        AcceleratorConfig.fromApiAcceleratorConfig(creationParameters.getAcceleratorConfig());

    String requestedLocation =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_NOTEBOOK_LOCATION, String.class);
    String newAttributes =
        DbSerDes.toJson(
            new ControlledAiNotebookInstanceAttributes(
                resource.getInstanceId(),
                requestedLocation,
                resource.getProjectId(),
                creationMachineType,
                creationAcceleratorConfig));

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