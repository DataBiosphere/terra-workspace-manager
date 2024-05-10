package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import java.util.Optional;

/**
 * Gets an Azure Managed Identity for a user's pet.
 *
 * <p>This implements the marker interface DeleteControlledResourceStep, in order to indicate that
 * it is also used when deleting the resource.
 */
public class GetPetManagedIdentityStep
    implements DeleteControlledResourceStep, GetManagedIdentityStep {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final Optional<String> userEmail;
  private final Optional<AuthenticatedUserRequest> userRequest;

  public GetPetManagedIdentityStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      String userEmail) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.userEmail = Optional.of(userEmail);
    this.userRequest = Optional.empty();
  }

  // This constructor will fetch the user's email from sam using the user request.
  public GetPetManagedIdentityStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      AuthenticatedUserRequest userRequest) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.userEmail = Optional.empty();
    this.userRequest = Optional.of(userRequest);
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Optional<String> userEmail = getUserEmail();
    if (userEmail.isEmpty()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new RuntimeException(
              "User email or user request was not provided to the GetPetManagedIdentityStep. This is a bug."));
    }
    return fetchManagedIdentity(context, userEmail.get());
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }

  private Optional<String> getUserEmail() throws InterruptedException {
    if (userEmail.isPresent()) {
      return userEmail;
    }
    if (userRequest.isPresent()) {
      return Optional.of(samService.getUserEmailFromSam(userRequest.get()));
    }
    return Optional.empty();
  }

  private StepResult fetchManagedIdentity(FlightContext context, String userEmail)
      throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    var objectId =
        samService.getOrCreateUserManagedIdentityForUser(
            userEmail,
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureResourceGroupId());

    try {
      var uami = msiManager.identities().getById(objectId);

      putManagedIdentityInContext(context, uami);

      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
  }
}
