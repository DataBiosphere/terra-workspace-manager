package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model.BatchPoolUserAssignedManagedIdentity;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchUserAssignedManagedIdentityStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(VerifyAzureBatchPoolCanBeCreatedStep.class);
  private final AuthenticatedUserRequest userRequest;
  private final SamService samService;
  private final ControlledAzureBatchPoolResource resource;

  public FetchUserAssignedManagedIdentityStep(
      AuthenticatedUserRequest userRequest,
      SamService samService,
      ControlledAzureBatchPoolResource resource) {
    this.userRequest = userRequest;
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
    return fetchUserAssignedManagedIdentity(context, azureCloudContext);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private StepResult fetchUserAssignedManagedIdentity(
      FlightContext flightContext, AzureCloudContext azureCloudContext) {
    String userEmail =
        resource.getAssignedUser().orElse(samService.getSamUser(userRequest).getEmail());
    logger.info(String.format("Fetching UAMI for '%s'", userEmail));
    String petManagedIdentityId =
        Rethrow.onInterrupted(
            () ->
                samService.getOrCreateUserManagedIdentityForUser(
                    userEmail,
                    azureCloudContext.getAzureSubscriptionId(),
                    azureCloudContext.getAzureTenantId(),
                    azureCloudContext.getAzureResourceGroupId()),
            "getPetManagedIdentity");

    logger.info(String.format(AzureBatchPoolHelper.PET_UAMI_FOUND, petManagedIdentityId));

    BatchPoolUserAssignedManagedIdentity azureUserAssignedManagedIdentity =
        new BatchPoolUserAssignedManagedIdentity(
            azureCloudContext.getAzureResourceGroupId(),
            parseAccountNameFromUserAssignedManagedIdentity(petManagedIdentityId),
            null);

    flightContext
        .getWorkingMap()
        .put(AzureBatchPoolHelper.BATCH_POOL_UAMI, azureUserAssignedManagedIdentity);
    return StepResult.getStepResultSuccess();
  }

  /**
   * Sam returns a user assigned managed identity that looks like:
   * /subscriptions/.../resourcegroups/.../providers/Microsoft.ManagedIdentity/userAssignedIdentities/pet-asdf1234
   * This function returns just the account name (e.g. pet-asdf1234)
   *
   * @param uami Fully qualified path to a UAMI
   * @return Name of the user assigned managed identity, which is a subset of the provided string.
   */
  private static String parseAccountNameFromUserAssignedManagedIdentity(String uami) {
    if (uami == null || uami.isEmpty()) {
      return "";
    }
    int lastSlashIndex = uami.lastIndexOf('/');
    return uami.substring(lastSlashIndex + 1);
  }
}
