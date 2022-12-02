package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.util.Context;
import com.azure.resourcemanager.monitor.MonitorManager;
import com.azure.resourcemanager.monitor.fluent.models.DataCollectionRuleAssociationProxyOnlyResourceInner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnableVmLoggingStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(EnableVmLoggingStep.class);
  public static final String DATA_COLLECTION_RULES_TYPE = "Microsoft.Insights/dataCollectionRules";

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public EnableVmLoggingStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureVmResource resource,
      LandingZoneApiDispatch landingZoneApiDispatch,
      AuthenticatedUserRequest userRequest) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    getDataCollectionRuleFromLandingZone()
        .ifPresent(
            dcr -> {
              MonitorManager monitorManager = getMonitorManager(context);

              final String vmResourceId =
                  context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_VM_ID, String.class);
              logger.info(
                  "configuring vm [{}] to use collection rule [{}]",
                  vmResourceId,
                  dcr.getResourceId());
              monitorManager
                  .diagnosticSettings()
                  .manager()
                  .serviceClient()
                  .getDataCollectionRuleAssociations()
                  .createWithResponse(
                      vmResourceId,
                      resource.getVmName(),
                      new DataCollectionRuleAssociationProxyOnlyResourceInner()
                          .withDataCollectionRuleId(dcr.getResourceId()),
                      Context.NONE);
            });
    return StepResult.getStepResultSuccess();
  }

  private MonitorManager getMonitorManager(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    return crlService.getMonitorManager(azureCloudContext, azureConfig);
  }

  private Optional<ApiAzureLandingZoneDeployedResource> getDataCollectionRuleFromLandingZone() {
    final UUID lzId =
        landingZoneApiDispatch.getLandingZoneId(
            new BearerToken(userRequest.getRequiredToken()), resource.getWorkspaceId());

    final List<ApiAzureLandingZoneDeployedResource> resources =
        listLandingZoneResources(
            new BearerToken(userRequest.getRequiredToken()), lzId, ResourcePurpose.SHARED_RESOURCE);
    logger.info(
        "shared resources in LZ [{}]: {}",
        lzId,
        String.join(",", resources.stream().map(r -> r.toString()).toList()));
    return resources.stream()
        .filter(r -> DATA_COLLECTION_RULES_TYPE.equalsIgnoreCase(r.getResourceType()))
        .findFirst();
  }

  private List<ApiAzureLandingZoneDeployedResource> listLandingZoneResources(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose purpose) {

    return landingZoneApiDispatch
        .listAzureLandingZoneResourcesByPurpose(bearerToken, landingZoneId, purpose)
        .getResources()
        .stream()
        .flatMap(r -> r.getDeployedResources().stream())
        .toList();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    getDataCollectionRuleFromLandingZone()
        .ifPresent(
            dcr -> {
              MonitorManager monitorManager = getMonitorManager(context);

              monitorManager
                  .diagnosticSettings()
                  .manager()
                  .serviceClient()
                  .getDataCollectionRuleAssociations()
                  .delete(
                      context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_VM_ID, String.class),
                      resource.getVmName());
            });
    return StepResult.getStepResultSuccess();
  }
}
