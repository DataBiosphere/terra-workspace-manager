package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;

public class DeleteAzureDiskFlightUtils {
  public static final String DISK_ATTACHED_VM_ID_KEY = "DISK_ATTACHED_VM_ID";

  private static final String DISK_RESOURCE_ID_FORMAT =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/disks/%s";

  private DeleteAzureDiskFlightUtils() {}

  public static AzureCloudContext getAzureCloudContext(FlightContext context) {
    return FlightUtils.getRequired(
        context.getWorkingMap(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
        AzureCloudContext.class);
  }

  public static String getAzureDiskResourceId(
      AzureCloudContext azureCloudContext, ControlledAzureDiskResource resource) {
    return String.format(
        DISK_RESOURCE_ID_FORMAT,
        azureCloudContext.getAzureSubscriptionId(),
        azureCloudContext.getAzureResourceGroupId(),
        resource.getDiskName());
  }
}
