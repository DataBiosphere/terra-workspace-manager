package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import java.util.List;
import java.util.UUID;

/**
 * Represents a means by which we can invoke landing zone related operations.
 *
 * <p>The intention is to abstract our "amalgamated" in-memory calls to the LZS library, so we can
 * introduce HTTP API client calls to the Landing Zone Service in their place as we move away from
 * the amalgamated implementation.
 */
public interface WorkspaceLandingZoneService {
  ApiCreateLandingZoneResult startLandingZoneCreationJob(
      BearerToken bearerToken,
      String jobId,
      UUID landingZoneId,
      String definition,
      String version,
      List<ApiAzureLandingZoneParameter> parameters,
      UUID billingProfileId,
      String asyncResultEndpoint)
      throws InterruptedException;

  ApiDeleteAzureLandingZoneResult startLandingZoneDeletionJob(
      BearerToken bearerToken, String jobId, UUID landingZoneId, String resultEndpoint)
      throws InterruptedException;

  ApiDeleteAzureLandingZoneJobResult getDeleteLandingZoneResult(
      BearerToken bearerToken, UUID landingZoneId, String jobId) throws InterruptedException;

  ApiAzureLandingZoneResult getAsyncJobResult(BearerToken bearerToken, String jobId)
      throws InterruptedException;

  ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId)
      throws InterruptedException;

  ApiAzureLandingZoneList listLandingZonesByBillingProfile(
      BearerToken bearerToken, UUID billingProfileId) throws InterruptedException;

  ApiAzureLandingZoneList listLandingZones(BearerToken bearerToken);

  String getLandingZoneRegion(BearerToken bearerToken, UUID landingZoneId)
      throws InterruptedException;

  ApiAzureLandingZoneDefinitionList listLandingZoneDefinitions(BearerToken bearerToken)
      throws InterruptedException;

  ApiAzureLandingZoneResourcesList listResourcesWithPurposes(
      BearerToken bearerToken, UUID landingZoneId) throws InterruptedException;

  ApiAzureLandingZoneResourcesList listResourcesMatchingPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose)
      throws InterruptedException;

  ApiResourceQuota getResourceQuota(BearerToken bearerToken, UUID landingZoneId, String resourceId);
}
