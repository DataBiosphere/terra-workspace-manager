package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_MACHINE_TYPE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;

public class UpdateNotebookCpuAndGpuAttributesStep implements Step {

  private final ControlledAiNotebookInstanceResource resource;
  private final ResourceDao resourceDao;

  public UpdateNotebookCpuAndGpuAttributesStep(
      ControlledAiNotebookInstanceResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String previousAttributes = resource.attributesToJson();
    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    String machineType = context.getInputParameters().get(UPDATE_MACHINE_TYPE, String.class);
    AcceleratorConfig acceleratorConfig =
        context.getInputParameters().get(UPDATE_ACCELERATOR_CONFIG, AcceleratorConfig.class);

    // If null, then don't update
    String newMachineType = machineType == null ? resource.getMachineType() : machineType;
    AcceleratorConfig newAcceleratorConfig =
        acceleratorConfig == null ? resource.getAcceleratorConfig() : acceleratorConfig;

    String newAttributes =
        DbSerDes.toJson(
            new ControlledAiNotebookInstanceAttributes(
                resource.getInstanceId(),
                resource.getLocation(),
                resource.getProjectId(),
                newMachineType,
                newAcceleratorConfig));
    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), null, null, newAttributes, null);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Revert database update
    String previousAttributes =
        context
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), null, null, previousAttributes, null);
    return StepResult.getStepResultSuccess();
  }
}
