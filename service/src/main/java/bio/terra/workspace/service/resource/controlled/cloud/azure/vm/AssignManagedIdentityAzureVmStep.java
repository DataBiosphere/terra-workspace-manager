package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.msi.MsiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssignManagedIdentityAzureVmStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(AssignManagedIdentityAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledAzureVmResource resource;

  public AssignManagedIdentityAzureVmStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledAzureVmResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.userRequest = userRequest;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputMap = context.getInputParameters();
    FlightUtils.validateRequiredEntries(inputMap, ControlledResourceKeys.CREATION_PARAMETERS);

    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    AzureProfile profile =
        new AzureProfile(
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureSubscriptionId(),
            AzureEnvironment.AZURE);
    MsiManager msiManager =
        MsiManager.configure()
            .authenticate(crlService.getManagedAppCredentials(azureConfig), profile);

    String petManagedIdentityId =
        SamRethrow.onInterrupted(
            () ->
                samService.getOrCreateUserManagedIdentity(
                    userRequest,
                    azureCloudContext.getAzureSubscriptionId(),
                    azureCloudContext.getAzureTenantId(),
                    azureCloudContext.getAzureResourceGroupId()),
            "getPetManagedIdentity");

    return AzureVmHelper.assignPetManagedIdentityToVm(
        azureCloudContext, computeManager, msiManager, resource.getVmName(), petManagedIdentityId);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    String petManagedIdentityId =
        samService.getOrCreateUserManagedIdentity(
            userRequest,
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureResourceGroupId());

    return AzureVmHelper.removePetManagedIdentitiesFromVm(
        azureCloudContext, computeManager, resource.getVmName(), petManagedIdentityId);
  }
}
