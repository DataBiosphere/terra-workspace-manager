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
      String asyncResultEndpoint);

  ApiDeleteAzureLandingZoneResult startLandingZoneDeletionJob(
      BearerToken bearerToken, String jobId, UUID landingZoneId, String resultEndpoint);

  ApiDeleteAzureLandingZoneJobResult getDeleteLandingZoneResult(
      BearerToken bearerToken, UUID landingZoneId, String jobId);

  ApiAzureLandingZoneResult getAsyncJobResult(BearerToken bearerToken, String jobId);

  ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId);

  ApiAzureLandingZoneList listLandingZonesByBillingProfile(
      BearerToken bearerToken, UUID billingProfileId);

  ApiAzureLandingZoneList listLandingZones(BearerToken bearerToken);

  String getLandingZoneRegion(BearerToken bearerToken, UUID landingZoneId);

  ApiAzureLandingZoneDefinitionList listLandingZoneDefinitions(BearerToken bearerToken);

  ApiAzureLandingZoneResourcesList listResourcesWithPurposes(
      BearerToken bearerToken, UUID landingZoneId);

  ApiAzureLandingZoneResourcesList listResourcesMatchingPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose);

  ApiResourceQuota getResourceQuota(BearerToken bearerToken, UUID landingZoneId, String resourceId);
}
