package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LandingZoneApiDispatch {
  private static final Logger logger = LoggerFactory.getLogger(LandingZoneApiDispatch.class);

  private static final String AZURE_STORAGE_ACCOUNT_RESOURCE_TYPE =
      "Microsoft.Storage/storageAccounts";
  private static final String AZURE_BATCH_ACCOUNT_RESOURCE_TYPE = "Microsoft.Batch/batchAccounts";
  private static final String AZURE_KUBERNETES_CLUSTER_RESOURCE_TYPE =
      "Microsoft.ContainerService/managedClusters";
  private static final String AZURE_DATABASE_RESOURCE_TYPE =
      "Microsoft.DBforPostgreSQL/flexibleServers";
  private static final String AZURE_UAMI_RESOURCE_TYPE =
      "Microsoft.ManagedIdentity/userAssignedIdentities";

  private final FeatureConfiguration features;
  private final SamService samService;
  private final WorkspaceLandingZoneService amalgamated;

  public LandingZoneApiDispatch(
      FeatureConfiguration features, SamService samService, LandingZoneServiceFactory lzsFactory) {
    this.features = features;
    this.samService = samService;
    this.amalgamated = lzsFactory.getLandingZoneService();
  }

  public ApiCreateLandingZoneResult createAzureLandingZone(
      BearerToken bearerToken,
      ApiCreateAzureLandingZoneRequestBody body,
      String asyncResultEndpoint) {
    features.azureEnabledCheck();
    logger.info(
        "Requesting new Azure landing zone with definition='{}', version='{}'",
        body.getDefinition(),
        body.getVersion());

    try {
      // Prevent deploying more than 1 landing zone per billing profile
      verifyLandingZoneDoesNotExistForBillingProfile(bearerToken, body);

      var result =
          Rethrow.onInterrupted(
              () ->
                  amalgamated.startLandingZoneCreationJob(
                      bearerToken,
                      body.getJobControl().getId(),
                      body.getLandingZoneId(),
                      body.getDefinition(),
                      body.getVersion(),
                      body.getParameters(),
                      body.getBillingProfileId(),
                      asyncResultEndpoint),
              "startLandingZoneCreationJob");
      if (result.getErrorReport() != null) {
        logger.warn(
            "Error creating landing zone. Status code:"
                + result.getErrorReport().getStatusCode()
                + " message:"
                + result.getErrorReport().getMessage());
      }
      return result;
    } catch (Exception e) {
      logger.warn(e.getMessage());
      throw e;
    }
  }

  private void verifyLandingZoneDoesNotExistForBillingProfile(
      BearerToken bearerToken, ApiCreateAzureLandingZoneRequestBody body) {
    // TODO: Catching the exception is a temp solution.
    // A better approach would be to return an empty list instead of throwing an exception
    try {
      Rethrow.onInterrupted(
          () ->
              amalgamated
                  .listLandingZonesByBillingProfile(bearerToken, body.getBillingProfileId())
                  .getLandingzones()
                  .stream()
                  .findFirst()
                  .ifPresent(
                      t -> {
                        throw new LandingZoneInvalidInputException(
                            "A Landing Zone already exists in the requested billing profile");
                      }),
          "listLandingZonesByBillingProfile");
    } catch (bio.terra.landingzone.db.exception.LandingZoneNotFoundException
        | LandingZoneServiceNotFoundException ex) {
      logger.info("The billing profile does not have a landing zone. ", ex);
    }
  }

  public ApiAzureLandingZoneResult getCreateAzureLandingZoneResult(
      BearerToken bearerToken, String jobId) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () -> amalgamated.getAsyncJobResult(bearerToken, jobId), "getAsyncJobResult");
  }

  public ApiAzureLandingZoneDefinitionList listAzureLandingZonesDefinitions(
      BearerToken bearerToken) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () -> amalgamated.listLandingZoneDefinitions(bearerToken), "listLandingZoneDefinitions");
  }

  public ApiDeleteAzureLandingZoneResult deleteLandingZone(
      BearerToken bearerToken,
      UUID landingZoneId,
      ApiDeleteAzureLandingZoneRequestBody body,
      String resultEndpoint) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () ->
            amalgamated.startLandingZoneDeletionJob(
                bearerToken, body.getJobControl().getId(), landingZoneId, resultEndpoint),
        "startLandingZoneDeletionJob");
  }

  public ApiAzureLandingZoneResourcesList listAzureLandingZoneResources(
      BearerToken bearerToken, UUID landingZoneId) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () -> amalgamated.listResourcesWithPurposes(bearerToken, landingZoneId),
        "listResourcesWithPurpose");
  }

  public Optional<ApiAzureLandingZoneDeployedResource> getSharedStorageAccount(
      BearerToken bearerToken, UUID landingZoneId) {
    return getSharedResourceByType(bearerToken, landingZoneId, AZURE_STORAGE_ACCOUNT_RESOURCE_TYPE);
  }

  public Optional<ApiAzureLandingZoneDeployedResource> getSharedBatchAccount(
      BearerToken bearerToken, UUID landingZoneId) {
    return getSharedResourceByType(bearerToken, landingZoneId, AZURE_BATCH_ACCOUNT_RESOURCE_TYPE);
  }

  public Optional<ApiAzureLandingZoneDeployedResource> getSharedKubernetesCluster(
      BearerToken bearerToken, UUID landingZoneId) {
    return getSharedResourceByType(
        bearerToken, landingZoneId, AZURE_KUBERNETES_CLUSTER_RESOURCE_TYPE);
  }

  public Optional<ApiAzureLandingZoneDeployedResource> getSharedDatabase(
      BearerToken bearerToken, UUID landingZoneId) {
    return getSharedResourceByType(bearerToken, landingZoneId, AZURE_DATABASE_RESOURCE_TYPE);
  }

  public Optional<ApiAzureLandingZoneDeployedResource> getSharedDatabaseAdminIdentity(
      BearerToken bearerToken, UUID landingZoneId) {
    return getResourceByTypeAndPurpose(
        bearerToken, landingZoneId, AZURE_UAMI_RESOURCE_TYPE, ResourcePurpose.POSTGRES_ADMIN);
  }

  public ApiAzureLandingZoneResourcesList listAzureLandingZoneResourcesByPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () -> amalgamated.listResourcesMatchingPurpose(bearerToken, landingZoneId, resourcePurpose),
        "listResourcesMatchingPurpose");
  }

  public UUID getLandingZoneId(BearerToken token, Workspace workspace) {
    Optional<UUID> profileId = workspace.getSpendProfileId().map(sp -> UUID.fromString(sp.getId()));

    if (profileId.isEmpty()) {
      throw new LandingZoneNotFoundException(
          String.format(
              "Landing zone could not be found. Workspace Id=%s doesn't have billing profile.",
              workspace.getWorkspaceId()));
    }

    // getLandingZonesByBillingProfile returns a list. But it always contains only one item
    var response =
        Rethrow.onInterrupted(
            () -> amalgamated.listLandingZonesByBillingProfile(token, profileId.get()),
            "listLandingZonesByBillingProfile");
    return response.getLandingzones().stream()
        .findFirst()
        .map(ApiAzureLandingZone::getLandingZoneId)
        .orElseThrow(
            () ->
                new LandingZoneNotFoundException(
                    String.format(
                        "Could not find a landing zone for the given billing profile: '%s'. Please"
                            + " check that the landing zone deployment is complete"
                            + " and that the caller has access to the landing zone resource.",
                        profileId.get())));
  }

  public ApiDeleteAzureLandingZoneJobResult getDeleteAzureLandingZoneResult(
      BearerToken token, UUID landingZoneId, String jobId) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () -> amalgamated.getDeleteLandingZoneResult(token, landingZoneId, jobId),
        "getDeleteLandingZoneResult");
  }

  public ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId) {
    features.azureEnabledCheck();
    return Rethrow.onInterrupted(
        () -> amalgamated.getAzureLandingZone(bearerToken, landingZoneId), "getAzureLandingZone");
  }

  public ApiAzureLandingZoneList listAzureLandingZones(
      BearerToken bearerToken, UUID billingProfileId) {
    features.azureEnabledCheck();
    if (billingProfileId != null) {
      return Rethrow.onInterrupted(
          () -> amalgamated.listLandingZonesByBillingProfile(bearerToken, billingProfileId),
          "listLandingZonesByBillingProfile");
    }
    return Rethrow.onInterrupted(
        () -> amalgamated.listLandingZonesByBillingProfile(bearerToken, null), "listLandingZones");
  }

  public ApiResourceQuota getResourceQuota(
      BearerToken bearerToken, UUID landingZoneId, String azureResourceId) {
    return amalgamated.getResourceQuota(bearerToken, landingZoneId, azureResourceId);
  }

  private Optional<ApiAzureLandingZoneDeployedResource> getSharedResourceByType(
      BearerToken bearerToken, UUID landingZoneId, String resourceType) {
    return getResourceByTypeAndPurpose(
        bearerToken, landingZoneId, resourceType, ResourcePurpose.SHARED_RESOURCE);
  }

  @NotNull
  private Optional<ApiAzureLandingZoneDeployedResource> getResourceByTypeAndPurpose(
      BearerToken bearerToken, UUID landingZoneId, String resourceType, ResourcePurpose purpose) {
    return listAzureLandingZoneResourcesByPurpose(bearerToken, landingZoneId, purpose)
        .getResources()
        .stream()
        .flatMap(r -> r.getDeployedResources().stream())
        .filter(r -> StringUtils.equalsIgnoreCase(r.getResourceType(), resourceType))
        .findFirst();
  }

  public ApiAzureLandingZone getLandingZone(
      AuthenticatedUserRequest userRequest, Workspace workspace) {
    final BearerToken token = new BearerToken(userRequest.getRequiredToken());
    var lzId = getLandingZoneId(token, workspace);
    return getAzureLandingZone(token, lzId);
  }

  /**
   * Fetches the region for a workspace from the parent landing zone.
   *
   * <p>NOTE: This uses the WSM SA administrative token to fetch region information. Callers should
   * ensure the user is authed prior to calling this method
   */
  public String getLandingZoneRegionForWorkspaceUsingWsmToken(Workspace workspace) {
    var token = new BearerToken(samService.getWsmServiceAccountToken());
    var lzId = getLandingZoneId(token, workspace);
    return getLandingZoneRegionUsingWsmToken(lzId);
  }

  /**
   * Fetches the region for a landing zone
   *
   * <p>NOTE: This uses the WSM SA administrative token to fetch region information. Callers should
   * ensure the user is authed prior to calling this method
   */
  public String getLandingZoneRegionUsingWsmToken(UUID landingZoneId) {
    features.azureEnabledCheck();
    var token = new BearerToken(samService.getWsmServiceAccountToken());
    return Rethrow.onInterrupted(
        () -> amalgamated.getLandingZoneRegion(token, landingZoneId), "getLandingZoneRegion");
  }
}
