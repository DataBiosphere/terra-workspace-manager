package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.monitor.MonitorManager;
import com.azure.resourcemanager.monitor.fluent.models.DataCollectionRuleAssociationProxyOnlyResourceInner;
import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnableVmLoggingStep implements Step {

  public static final String EXTENSION_NAME = "AzureMonitorLinuxAgent";
  private static final Logger logger = LoggerFactory.getLogger(EnableVmLoggingStep.class);
  public static final String DATA_COLLECTION_RULES_TYPE = "Microsoft.Insights/dataCollectionRules";

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;

  public EnableVmLoggingStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureVmResource resource,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      getDataCollectionRuleFromLandingZone()
          .ifPresent(
              dcr -> {
                final String vmId =
                    context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_VM_ID, String.class);

                addMonitorAgentToVm(context, vmId);

                createDataCollectionRuleAssociation(context, dcr, vmId);
              });
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
  }

  @WithSpan
  @VisibleForTesting
  void createDataCollectionRuleAssociation(
      FlightContext context, ApiAzureLandingZoneDeployedResource dcr, String vmId) {
    try {
      getMonitorManager(context)
          .diagnosticSettings()
          .manager()
          .serviceClient()
          .getDataCollectionRuleAssociations()
          .createWithResponse(
              vmId,
              resource.getVmName(),
              new DataCollectionRuleAssociationProxyOnlyResourceInner()
                  .withDataCollectionRuleId(dcr.getResourceId()),
              Context.NONE);
    } catch (ManagementException e) {
      if (e.getResponse().getStatusCode() == 409) {
        logger.info(
            "data collection rule association already exists for vm {} and dcr {}",
            vmId,
            dcr.getResourceId());
      } else {
        throw e;
      }
    }
  }

  @WithSpan
  private void addMonitorAgentToVm(FlightContext context, String vmId) {
    var virtualMachine = getComputeManager(context).virtualMachines().getById(vmId);
    removeExtension(vmId, virtualMachine);
    createExtension(context, virtualMachine);
  }

  @VisibleForTesting
  void createExtension(FlightContext context, VirtualMachine virtualMachine) {
    var extension =
        virtualMachine
            .update()
            .defineNewExtension(EXTENSION_NAME)
            .withPublisher("Microsoft.Azure.Monitor")
            .withType("AzureMonitorLinuxAgent")
            .withVersion(azureConfig.getAzureMonitorLinuxAgentVersion())
            .withMinorVersionAutoUpgrade();

    // use the pet identity if one is defined, otherwise a system identity will be used
    Optional.ofNullable(
            context
                .getWorkingMap()
                .get(GetManagedIdentityStep.MANAGED_IDENTITY_RESOURCE_ID, String.class))
        .ifPresentOrElse(
            (petId ->
                extension.withPublicSetting(
                    "authentication",
                    Map.of(
                        "managedIdentity",
                        Map.of("identifier-name", "mi_res_id", "identifier-value", petId)))),
            (() -> enableSystemAssignedIdentity(virtualMachine)));

    extension.attach().apply();
  }

  /**
   * If the EXTENSION_NAME exists on the `virtualMachine`, remove it. Otherwise, no-op.
   *
   * @param vmId
   * @param virtualMachine
   */
  @VisibleForTesting
  void removeExtension(String vmId, VirtualMachine virtualMachine) {
    Optional.ofNullable(virtualMachine.listExtensions().get(EXTENSION_NAME))
        .ifPresent(
            (extension) -> {
              logger.info(
                  "extension {} already exists on vm {}, deleting it to retry",
                  EXTENSION_NAME,
                  vmId);
              virtualMachine.update().withoutExtension(EXTENSION_NAME).apply();
            });
  }

  private void enableSystemAssignedIdentity(VirtualMachine virtualMachine) {
    logger.info("enabling system assigned managed identity for vm {}", virtualMachine.id());
    virtualMachine.update().withSystemAssignedManagedServiceIdentity().apply();
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

  private ComputeManager getComputeManager(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    return crlService.getComputeManager(azureCloudContext, azureConfig);
  }

  Optional<ApiAzureLandingZoneDeployedResource> getDataCollectionRuleFromLandingZone() {
    try {
      var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
      final UUID lzId =
          landingZoneApiDispatch.getLandingZoneId(
              bearerToken, workspaceService.getWorkspace(resource.getWorkspaceId()));

      return listLandingZoneResources(bearerToken, lzId, ResourcePurpose.SHARED_RESOURCE).stream()
          .filter(r -> DATA_COLLECTION_RULES_TYPE.equalsIgnoreCase(r.getResourceType()))
          .findFirst();
    } catch (LandingZoneNotFoundException notFound) {
      logger.info(
          "landing zone not found for workspace %s".formatted(resource.getWorkspaceId()), notFound);
      return Optional.empty();
    }
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
