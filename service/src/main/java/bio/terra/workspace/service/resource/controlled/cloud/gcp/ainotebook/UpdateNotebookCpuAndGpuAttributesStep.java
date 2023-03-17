package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_MACHINE_TYPE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Optional;

// Update the CPU and GPU attributes in the database.
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
    FlightMap workingMap = context.getWorkingMap();

    workingMap.put(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    // Use the effective update instructions calculated from the previous step.
    String effectiveMachineType = workingMap.get(UPDATE_MACHINE_TYPE, String.class);
    AcceleratorConfig effectiveAcceleratorConfig =
        workingMap.get(UPDATE_ACCELERATOR_CONFIG, AcceleratorConfig.class);

    if (effectiveMachineType == null && effectiveAcceleratorConfig == null) {
      return StepResult.getStepResultSuccess();
    }

    String newAttributes =
        DbSerDes.toJson(
            new ControlledAiNotebookInstanceAttributes(
                resource.getInstanceId(),
                resource.getLocation(),
                resource.getProjectId(),
                Optional.ofNullable(effectiveMachineType).orElse(resource.getMachineType()),
                Optional.ofNullable(effectiveAcceleratorConfig)
                    .orElse(resource.getAcceleratorConfig())));
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
