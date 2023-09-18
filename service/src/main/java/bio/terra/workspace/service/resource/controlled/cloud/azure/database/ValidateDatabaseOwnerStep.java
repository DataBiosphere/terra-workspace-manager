package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.Optional;

public class ValidateDatabaseOwnerStep implements Step {
  private final ControlledAzureDatabaseResource resource;
  private final ResourceDao resourceDao;

  public ValidateDatabaseOwnerStep(
      ControlledAzureDatabaseResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (resource.getDatabaseOwner() != null && getOwnerManagedIdentity().isEmpty()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new BadRequestException(
              String.format(
                  "An Azure Managed Identity with id %s does not exist in workspace %s",
                  resource.getDatabaseOwner(), resource.getWorkspaceId())));
    }

    return StepResult.getStepResultSuccess();
  }

  private Optional<WsmResource> getOwnerManagedIdentity() {
    try {
      var ownerResource =
          resourceDao.getResourceByName(resource.getWorkspaceId(), resource.getDatabaseOwner());
      if (ownerResource.getResourceType() == WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY) {
        return Optional.of(ownerResource);
      } else {
        return Optional.empty();
      }
    } catch (ResourceNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
