package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.common.utils.MapperUtils;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolDeploymentConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolVirtualMachineConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolVirtualMachineImageReference;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import java.util.UUID;

public class ControlledResourceBatchPoolFixtures {
  private static final String BATCH_POOL_ID = UUID.randomUUID().toString();
  private static final String BATCH_POOL_VM_SIZE = "Standard_D2s_v3";
  private static final String NODE_AGENT_SKU_ID = "batch.node.ubuntu 18.04";
  private static final String IMAGE_REFERENCE_PUBLISHER = "canonical";
  private static final String IMAGE_REFERENCE_OFFER = "ubuntuserver";
  private static final String IMAGE_REFERENCE_SKU = "18.04-lts";

  public static ApiAzureBatchPoolCreationParameters createBatchPoolWithRequiredParameters() {
    var imageReference =
        new ApiAzureBatchPoolVirtualMachineImageReference()
            .offer(IMAGE_REFERENCE_OFFER)
            .publisher(IMAGE_REFERENCE_PUBLISHER)
            .sku(IMAGE_REFERENCE_SKU);
    var virtualMachineConfiguration =
        new ApiAzureBatchPoolVirtualMachineConfiguration()
            .imageReference(imageReference)
            .nodeAgentSkuId(NODE_AGENT_SKU_ID);
    var deploymentConfiguration =
        new ApiAzureBatchPoolDeploymentConfiguration()
            .virtualMachineConfiguration(virtualMachineConfiguration);
    return new ApiAzureBatchPoolCreationParameters()
        .id(BATCH_POOL_ID)
        .vmSize(BATCH_POOL_VM_SIZE)
        .deploymentConfiguration(deploymentConfiguration);
  }

  public static ControlledAzureBatchPoolResource createAzureBatchPoolResource(
      ApiAzureBatchPoolCreationParameters creationParameters,
      ControlledResourceFields commonFields) {
    return ControlledAzureBatchPoolResource.builder()
        .id(creationParameters.getId())
        .vmSize(creationParameters.getVmSize())
        .deploymentConfiguration(
            MapperUtils.BatchPoolMapper.mapFrom(creationParameters.getDeploymentConfiguration()))
        .common(commonFields)
        .build();
  }
}
