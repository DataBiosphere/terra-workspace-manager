package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPetManagedIdentityStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetPetManagedIdentityStep.class);
  private final SamService samService;
  private final ControlledAzureVmResource resource;

  public GetPetManagedIdentityStep(SamService samService, ControlledAzureVmResource resource) {
    this.samService = samService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);

    // If there is a private resource user, request a pet for that user and store
    // it in the working map.
    if (resource.getAssignedUser().isPresent()) {
      String petManagedIdentityId =
          Rethrow.onInterrupted(
              () ->
                  samService.getOrCreateUserManagedIdentityForUser(
                      resource.getAssignedUser().get(),
                      azureCloudContext.getAzureSubscriptionId(),
                      azureCloudContext.getAzureTenantId(),
                      azureCloudContext.getAzureResourceGroupId()),
              "getPetManagedIdentity");
      context.getWorkingMap().put(AzureVmHelper.WORKING_MAP_PET_ID, petManagedIdentityId);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
