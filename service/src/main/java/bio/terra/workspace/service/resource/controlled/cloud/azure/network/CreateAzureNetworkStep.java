package bio.terra.workspace.service.resource.controlled.cloud.azure.network;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateNetworkRequestData;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateNetworkSecurityGroupRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.SecurityRuleProtocol;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates an Azure Network address. Designed to run directly after {@link GetAzureNetworkStep}. */
public class CreateAzureNetworkStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureNetworkStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureNetworkResource resource;

  public CreateAzureNetworkStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureNetworkResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    try {
      NetworkSecurityGroup subnetNsg =
          computeManager
              .networkManager()
              .networkSecurityGroups()
              .define(resource.getSubnetName())
              .withRegion(resource.getRegion())
              .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
              .withTag("workspaceId", resource.getWorkspaceId().toString())
              .withTag("resourceId", resource.getResourceId().toString())
              .defineRule("AllowHttpInComing")
              .allowInbound()
              .fromAddress("INTERNET")
              .fromAnyPort()
              .toAnyAddress()
              .toPort(8080)
              .withProtocol(SecurityRuleProtocol.TCP)
              .attach()
              .create(
                  Defaults.buildContext(
                      CreateNetworkSecurityGroupRequestData.builder()
                          .setName(resource.getSubnetName())
                          .setRegion(Region.fromName(resource.getRegion()))
                          .setTenantId(azureCloudContext.getAzureTenantId())
                          .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                          .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                          .setRules(Collections.emptyList())
                          .build()));

      computeManager
          .networkManager()
          .networks()
          .define(resource.getNetworkName())
          .withRegion(resource.getRegion())
          .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
          .withTag("workspaceId", resource.getWorkspaceId().toString())
          .withTag("resourceId", resource.getResourceId().toString())
          .withAddressSpace(resource.getAddressSpaceCidr())
          .defineSubnet(resource.getSubnetName())
          .withAddressPrefix(resource.getSubnetAddressCidr())
          .withExistingNetworkSecurityGroup(subnetNsg)
          .attach()
          .create(
              Defaults.buildContext(
                  CreateNetworkRequestData.builder()
                      .setName(resource.getNetworkName())
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setRegion(Region.fromName(resource.getRegion()))
                      .setSubnetName(resource.getSubnetName())
                      .setNetworkSecurityGroup(subnetNsg)
                      .setAddressPrefix(resource.getSubnetAddressCidr())
                      .setAddressSpaceCidr(resource.getAddressSpaceCidr())
                      .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (ManagementExceptionUtils.isExceptionCode(e, ManagementExceptionUtils.CONFLICT)) {
        logger.info(
            "Azure Network {} in managed resource group {} already exists",
            resource.getNetworkName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.SUBNETS_NOT_IN_SAME_VNET)) {
        logger.info(
            "Azure Network {} and Subnet {} in managed resource group {} must belong to the same virtual network",
            resource.getNetworkName(),
            resource.getSubnetName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    try {
      computeManager
          .networkManager()
          .networks()
          .deleteByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getNetworkName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Azure Network {} in managed resource group {} already deleted",
            resource.getNetworkName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
