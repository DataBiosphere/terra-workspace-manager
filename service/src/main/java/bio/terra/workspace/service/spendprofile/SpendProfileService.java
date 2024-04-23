package bio.terra.workspace.service.spendprofile;

import bio.terra.common.exception.ValidationException;
import bio.terra.common.tracing.JakartaTracingFilter;
import bio.terra.profile.api.ProfileApi;
import bio.terra.profile.client.ApiClient;
import bio.terra.profile.client.ApiException;
import bio.terra.profile.model.CreateProfileRequest;
import bio.terra.profile.model.ProfileModel;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.spendprofile.exceptions.BillingProfileManagerServiceAPIException;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.spendprofile.model.SpendProfileOrganization;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.google.api.client.util.Strings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.client.Client;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * A service for retrieving and authorizing the use of {@link SpendProfile}s.
 *
 * <p>If enabled, calls out to Billing Profile Manager to fetch the relevant spend data. Otherwise,
 * fetches from our in-memory configuration.
 */
@Component
public class SpendProfileService {
  private final Logger logger = LoggerFactory.getLogger(SpendProfileService.class);
  private final SamService samService;
  private final Map<SpendProfileId, SpendProfile> spendProfiles;
  private final SpendProfileConfiguration spendProfileConfiguration;
  private final OpenTelemetry openTelemetry;
  private final Client commonHttpClient;

  @Autowired
  public SpendProfileService(
      SamService samService,
      SpendProfileConfiguration spendProfileConfiguration,
      OpenTelemetry openTelemetry) {
    this(
        samService,
        adaptConfigurationModels(spendProfileConfiguration.getSpendProfiles()),
        spendProfileConfiguration,
        openTelemetry);
  }

  /** This constructor is only used for unit testing. DO NOT USE FOR PRODUCTION */
  @VisibleForTesting
  public SpendProfileService(
      SamService samService,
      List<SpendProfile> spendProfiles,
      SpendProfileConfiguration spendProfileConfiguration,
      OpenTelemetry openTelemetry) {
    this.samService = samService;
    this.spendProfiles = Maps.uniqueIndex(spendProfiles, SpendProfile::id);
    this.spendProfileConfiguration = spendProfileConfiguration;
    this.openTelemetry = openTelemetry;

    this.commonHttpClient =
        new ApiClient().getHttpClient().register(new JakartaTracingFilter(openTelemetry));
  }

  /**
   * Authorize the user to link the Spend Profile. Returns the {@link SpendProfile} associated with
   * the id if there is one and the user is authorized to link it. Otherwise, throws a {@link
   * SpendUnauthorizedException}.
   */
  @WithSpan
  public SpendProfile authorizeLinking(
      SpendProfileId spendProfileId, boolean bpmEnabled, AuthenticatedUserRequest userRequest) {

    SpendProfile spend = null;
    if (spendProfiles.containsKey(spendProfileId)) {
      if (!Rethrow.onInterrupted(
          () ->
              samService.isAuthorized(
                  userRequest,
                  SamConstants.SamResource.SPEND_PROFILE,
                  spendProfileId.getId(),
                  SamConstants.SamSpendProfileAction.LINK),
          "isAuthorized")) {
        throw SpendUnauthorizedException.linkUnauthorized(spendProfileId);
      }
      spend = spendProfiles.get(spendProfileId);
    } else if (bpmEnabled) {
      // profiles returned from BPM means we are auth'ed
      spend = getSpendProfileFromBpm(userRequest, spendProfileId);
    } else {
      if (!Rethrow.onInterrupted(
          () ->
              samService.isAuthorized(
                  userRequest,
                  SamConstants.SamResource.SPEND_PROFILE,
                  spendProfileId.getId(),
                  SamConstants.SamSpendProfileAction.LINK),
          "isAuthorized")) {
        throw SpendUnauthorizedException.linkUnauthorized(spendProfileId);
      }
    }

    if (spend == null) {
      // We throw an unauthorized exception when we do not know about the Spend Profile to match
      // Sam's behavior. Sam authz check does not reveal if the resource does not exist vs the user
      // does not have access to it. In practice, however, something is probably misconfigured on
      // Workspace Manager if the authz check from Sam passes but we don't know about the spend
      // profile, so log a warning to help debug.
      logger.warn("Sam spend link authz succeeded but spend profile unknown: {}", spendProfileId);
      throw SpendUnauthorizedException.linkUnauthorized(spendProfileId);
    }
    return spend;
  }

  private static List<SpendProfile> adaptConfigurationModels(
      List<SpendProfileConfiguration.SpendProfileModel> spendModels) {
    return spendModels.stream()
        .filter(
            // filter out empty profiles
            spendModel ->
                !Strings.isNullOrEmpty(spendModel.getBillingAccountId())
                    && !Strings.isNullOrEmpty(spendModel.getId()))
        .map(
            spendModel ->
                new SpendProfile(
                    new SpendProfileId(spendModel.getId()),
                    CloudPlatform.GCP,
                    spendModel.getBillingAccountId(),
                    null,
                    null,
                    null,
                    null))
        .collect(Collectors.toList());
  }

  public SpendProfile getSpendProfile(
      SpendProfileId spendProfileId, AuthenticatedUserRequest userRequest) {
    SpendProfile spend;
    if (spendProfiles.containsKey(spendProfileId)) {
      spend = spendProfiles.get(spendProfileId);
    } else {
      // profiles returned from BPM means we are auth'ed
      spend = getSpendProfileFromBpm(userRequest, spendProfileId);
    }

    return spend;
  }

  @WithSpan
  private SpendProfile getSpendProfileFromBpm(
      AuthenticatedUserRequest userRequest, SpendProfileId spendProfileId) {
    SpendProfile spend;
    var profileApi = getProfileApi(userRequest);
    try {
      var profile = profileApi.getProfile(UUID.fromString(spendProfileId.getId()));
      logger.info(
          "Retrieved billing profile ID {} from billing profile manager",
          profile.getId().toString());
      spend =
          new SpendProfile(
              spendProfileId,
              getProfileCloudPlatform(profile),
              profile.getBillingAccountId(),
              profile.getTenantId(),
              profile.getSubscriptionId(),
              profile.getManagedResourceGroupId(),
              Optional.ofNullable(profile.getOrganization())
                  .map(SpendProfileOrganization::new)
                  .orElse(null));
    } catch (ApiException ex) {
      if (ex.getCode() == HttpStatus.FORBIDDEN.value()) {
        return null;
      } else {
        throw new BillingProfileManagerServiceAPIException(ex);
      }
    }

    return spend;
  }

  private CloudPlatform getProfileCloudPlatform(ProfileModel profile) {
    if (profile.getCloudPlatform().equals(bio.terra.profile.model.CloudPlatform.GCP)) {
      return CloudPlatform.GCP;
    } else if (profile.getCloudPlatform().equals(bio.terra.profile.model.CloudPlatform.AZURE)) {
      return CloudPlatform.AZURE;
    } else {
      throw new ValidationException(
          String.format(
              "Invalid cloud platform for billing profile id %s: %s ",
              profile.getId().toString(), profile.getCloudPlatform().getValue()));
    }
  }

  /**
   * Creates a spend profile via the billing profile manager service, intended for usage in tests
   * only.
   */
  @VisibleForTesting
  public SpendProfile createGcpSpendProfile(
      String billingAccountId,
      String displayName,
      String biller,
      AuthenticatedUserRequest userRequest) {
    CreateProfileRequest req =
        new CreateProfileRequest()
            .id(UUID.randomUUID())
            .billingAccountId(billingAccountId)
            .displayName(displayName)
            .biller(biller)
            .cloudPlatform(bio.terra.profile.model.CloudPlatform.GCP);
    try {
      var rawModel = getProfileApi(userRequest).createProfile(req);
      return SpendProfile.buildGcpSpendProfile(
          new SpendProfileId(rawModel.getId().toString()), rawModel.getBillingAccountId());
    } catch (ApiException e) {
      throw new BillingProfileManagerServiceAPIException(e);
    }
  }

  /** Deletes a profile in the billing profile manager service, intended for usage in tests only */
  @VisibleForTesting
  public void deleteProfile(UUID profileId, AuthenticatedUserRequest userRequest) {
    try {
      getProfileApi(userRequest).deleteProfile(profileId);
    } catch (ApiException e) {
      throw new BillingProfileManagerServiceAPIException(e);
    }
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = new ApiClient().setHttpClient(commonHttpClient);
    apiClient.setAccessToken(accessToken);
    apiClient.setBasePath(this.spendProfileConfiguration.getBasePath());
    return apiClient;
  }

  private ProfileApi getProfileApi(AuthenticatedUserRequest userRequest) {
    return new ProfileApi(getApiClient(userRequest.getRequiredToken()));
  }
}
