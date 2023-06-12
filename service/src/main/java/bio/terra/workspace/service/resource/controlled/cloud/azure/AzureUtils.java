package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;

public class AzureUtils {
  public static String getResourceName(ApiAzureLandingZoneDeployedResource deployedResource) {
    var idParts = deployedResource.getResourceId().split("/");
    return idParts[idParts.length - 1];
  }
}
