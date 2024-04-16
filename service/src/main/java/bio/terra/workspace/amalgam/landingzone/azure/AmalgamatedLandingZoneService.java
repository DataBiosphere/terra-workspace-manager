package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.ConflictException;
import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneRequest;
import bio.terra.workspace.common.utils.MapperUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This is the "amalgamated" implementation of our landing zone service layer. It makes calls
 * directly to the landing zone library rather than via an HTTP API. NOTE: This is a legacy class
 * intended to ease the transition to the newer HTTP LZS service.
 */
@Deprecated
@Component
public class AmalgamatedLandingZoneService implements WorkspaceLandingZoneService {

  private final LandingApiClientTypeAdapter typeAdapter;
  private final LandingZoneService landingZoneService;

  @Autowired
  public AmalgamatedLandingZoneService(LandingZoneService landingZoneService) {
    this.landingZoneService = landingZoneService;
    this.typeAdapter = new LandingApiClientTypeAdapter();
  }

  @Override
  public ApiCreateLandingZoneResult startLandingZoneCreationJob(
      BearerToken bearerToken,
      String jobId,
      UUID landingZoneId,
      String definition,
      String version,
      List<ApiAzureLandingZoneParameter> parameters,
      UUID billingProfileId,
      String asyncResultEndpoint) {

    LandingZoneRequest landingZoneRequest =
        LandingZoneRequest.builder()
            .landingZoneId(landingZoneId)
            .definition(definition)
            .version(version)
            .parameters(MapperUtils.LandingZoneMapper.landingZoneParametersFrom(parameters))
            .billingProfileId(billingProfileId)
            .build();
    var result =
        landingZoneService.startLandingZoneCreationJob(
            bearerToken, jobId, landingZoneRequest, asyncResultEndpoint);

    return typeAdapter.toApiCreateLandingZoneResult(result);
  }

  @Override
  public ApiDeleteAzureLandingZoneResult startLandingZoneDeletionJob(
      BearerToken bearerToken, String jobId, UUID landingZoneId, String resultEndpoint) {
    var result =
        landingZoneService.startLandingZoneDeletionJob(
            bearerToken, jobId, landingZoneId, resultEndpoint);
    return typeAdapter.toApiDeleteAzureLandingZoneResult(result);
  }

  @Override
  public ApiDeleteAzureLandingZoneJobResult getDeleteLandingZoneResult(
      BearerToken bearerToken, UUID landingZoneId, String jobId) {
    var response = landingZoneService.getAsyncDeletionJobResult(bearerToken, landingZoneId, jobId);
    return typeAdapter.toApiDeleteAzureLandingZoneJobResult(response);
  }

  @Override
  public ApiAzureLandingZoneResult getAsyncJobResult(BearerToken bearerToken, String jobId) {
    var response = landingZoneService.getAsyncJobResult(bearerToken, jobId);
    return typeAdapter.toApiAzureLandingZoneResult(response);
  }

  @Override
  public ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId) {
    LandingZone landingZoneRecord = landingZoneService.getLandingZone(bearerToken, landingZoneId);
    return typeAdapter.toApiAzureLandingZone(landingZoneRecord);
  }

  @Override
  public ApiAzureLandingZoneList listLandingZonesByBillingProfile(
      BearerToken bearerToken, UUID billingProfileId) {
    ApiAzureLandingZoneList result = new ApiAzureLandingZoneList();
    List<LandingZone> landingZones =
        landingZoneService.getLandingZonesByBillingProfile(bearerToken, billingProfileId);
    if (landingZones.size() > 0) {
      // The enforced logic is 1:1 relation between Billing Profile and a Landing Zone.
      // The landing zone service returns one record in the list if landing zone exists
      // for a given billing profile.
      if (landingZones.size() == 1) {
        result.addLandingzonesItem(typeAdapter.toApiAzureLandingZone(landingZones.get(0)));
      } else {
        throw new ConflictException(
            String.format(
                "There are more than one landing zone found for the given billing profile: '%s'. Please"
                    + " check the landing zone deployment is correct.",
                billingProfileId));
      }
    }
    return result;
  }

  @Override
  public ApiAzureLandingZoneList listLandingZones(BearerToken bearerToken) {
    List<LandingZone> landingZones = landingZoneService.listLandingZones(bearerToken);
    return new ApiAzureLandingZoneList()
        .landingzones(
            landingZones.stream()
                .map(typeAdapter::toApiAzureLandingZone)
                .collect(Collectors.toList()));
  }

  @Override
  public String getLandingZoneRegion(BearerToken bearerToken, UUID landingZoneId) {
    return landingZoneService.getLandingZoneRegion(bearerToken, landingZoneId);
  }

  @Override
  public ApiAzureLandingZoneDefinitionList listLandingZoneDefinitions(BearerToken bearerToken) {
    var templates = landingZoneService.listLandingZoneDefinitions(bearerToken);
    return new ApiAzureLandingZoneDefinitionList()
        .landingzones(
            templates.stream()
                .map(
                    t ->
                        new ApiAzureLandingZoneDefinition()
                            .definition(t.definition())
                            .name(t.name())
                            .description(t.description())
                            .version(t.version()))
                .collect(Collectors.toList()));
  }

  @Override
  public ApiAzureLandingZoneResourcesList listResourcesWithPurposes(
      BearerToken bearerToken, UUID landingZoneId) {
    var result = new ApiAzureLandingZoneResourcesList().id(landingZoneId);
    landingZoneService
        .listResourcesWithPurposes(bearerToken, landingZoneId)
        .deployedResources()
        .forEach(
            (p, dp) ->
                result.addResourcesItem(
                    new ApiAzureLandingZoneResourcesPurposeGroup()
                        .purpose(p.toString())
                        .deployedResources(
                            dp.stream()
                                .map(r -> typeAdapter.toApiAzureLandingZoneDeployedResource(r, p))
                                .toList())));

    return result;
  }

  @Override
  public ApiAzureLandingZoneResourcesList listResourcesMatchingPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose) {
    var result = new ApiAzureLandingZoneResourcesList().id(landingZoneId);
    var deployedResources =
        landingZoneService
            .listResourcesByPurpose(bearerToken, landingZoneId, resourcePurpose)
            .stream()
            .map(r -> typeAdapter.toApiAzureLandingZoneDeployedResource(r, resourcePurpose))
            .toList();
    result.addResourcesItem(
        new ApiAzureLandingZoneResourcesPurposeGroup()
            .purpose(resourcePurpose.toString())
            .deployedResources(deployedResources));
    return result;
  }

  @Override
  public ApiResourceQuota getResourceQuota(
      BearerToken bearerToken, UUID landingZoneId, String resourceId) {
    var response = landingZoneService.getResourceQuota(bearerToken, landingZoneId, resourceId);
    return typeAdapter.toApiResourceQuota(landingZoneId, response);
  }
}
