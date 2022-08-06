package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.cloudres.azure.landingzones.management.LandingZoneManager;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.crl.CrlService;
import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AzureLandingZoneManagerProvider {

  private final CrlService crlService;
  private final AzureConfiguration azureConfiguration;

  @Autowired
  public AzureLandingZoneManagerProvider(
      CrlService crlService, AzureConfiguration azureConfiguration) {
    this.crlService = crlService;
    this.azureConfiguration = azureConfiguration;
  }

  public LandingZoneManager createLandingZoneManager(ApiAzureContext apiAzureContext) {
    TokenCredential credential = crlService.getManagedAppCredentials(azureConfiguration);
    var azureProfile =
        new AzureProfile(
            apiAzureContext.getTenantId(),
            apiAzureContext.getSubscriptionId(),
            AzureEnvironment.AZURE);
    return LandingZoneManager.createLandingZoneManager(
        credential, azureProfile, apiAzureContext.getResourceGroupId());
  }
}
