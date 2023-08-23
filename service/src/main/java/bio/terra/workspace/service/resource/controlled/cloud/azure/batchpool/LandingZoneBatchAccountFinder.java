package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LandingZoneBatchAccountFinder {
  private static final Logger logger = LoggerFactory.getLogger(LandingZoneBatchAccountFinder.class);

  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final WorkspaceService workspaceService;

  public LandingZoneBatchAccountFinder(
      LandingZoneApiDispatch landingZoneApiDispatch, WorkspaceService workspaceService) {
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.workspaceService = workspaceService;
  }

  public Optional<ApiAzureLandingZoneDeployedResource> find(
      String userRequestToken, ControlledAzureBatchPoolResource resource) {
    try {
      UUID landingZoneId =
          landingZoneApiDispatch.getLandingZoneId(
              new BearerToken(userRequestToken),
              workspaceService.getWorkspace(resource.getWorkspaceId()));
      Optional<ApiAzureLandingZoneDeployedResource> existingSharedBatchAccount =
          landingZoneApiDispatch.getSharedBatchAccount(
              new BearerToken(userRequestToken), landingZoneId);
      if (existingSharedBatchAccount.isPresent()) {
        logger.info(
            String.format(
                AzureBatchPoolHelper.SHARED_BATCH_ACCOUNT_FOUND_IN_LANDING_ZONE, landingZoneId));
      } else {
        logger.warn(
            String.format(
                AzureBatchPoolHelper.SHARED_BATCH_ACCOUNT_NOT_FOUND_IN_LANDING_ZONE,
                landingZoneId));
      }
      return existingSharedBatchAccount;
    } catch (LandingZoneNotFoundException e) { // Thrown by landingZoneApiDispatch
      logger.warn("Could not check existence of shared batch account. Error='{}'", e.getMessage());
      return Optional.empty();
    }
  }
}
