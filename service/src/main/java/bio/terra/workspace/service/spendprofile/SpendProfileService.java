package bio.terra.workspace.service.spendprofile;

import bio.terra.profile.client.ApiException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.spendprofile.client.BpmClientProvider;
import bio.terra.workspace.service.spendprofile.exceptions.BillingProfileManagerServiceAPIException;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
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
 * <p>TODO: Integrate with the Spend Profile Manager component instead of doing our own in-memory
 * configuration of spend profiles.
 */
@Component
public class SpendProfileService {
  private final Logger logger = LoggerFactory.getLogger(SpendProfileService.class);
  private final SamService samService;
  private final Map<SpendProfileId, SpendProfile> spendProfiles;
  private final boolean bpmEnabled;
  private final BpmClientProvider bpmClientProvider;

  @Autowired
  public SpendProfileService(
      SamService samService,
      SpendProfileConfiguration spendProfileConfiguration,
      BpmClientProvider bpmClientProvider,
      FeatureConfiguration features) {
    this(
        samService,
        parse(spendProfileConfiguration.getSpendProfiles()),
        bpmClientProvider,
        features);
  }

  public SpendProfileService(
      SamService samService,
      List<SpendProfile> spendProfiles,
      BpmClientProvider clientProvider,
      FeatureConfiguration features) {
    this.samService = samService;
    this.spendProfiles = Maps.uniqueIndex(spendProfiles, SpendProfile::id);
    this.bpmClientProvider = clientProvider;
    this.bpmEnabled = features.isBpmEnabled();
  }

  /**
   * Authorize the user to link the Spend Profile. Returns the {@link SpendProfile} associated with
   * the id if there is one and the user is authorized to link it. Otherwise, throws a {@link
   * SpendUnauthorizedException}.
   *
   * @throws
   */
  public SpendProfile authorizeLinking(
      SpendProfileId spendProfileId, AuthenticatedUserRequest userRequest) {
    if (!SamRethrow.onInterrupted(
        () ->
            samService.isAuthorized(
                userRequest,
                SamConstants.SamResource.SPEND_PROFILE,
                spendProfileId.getId(),
                SamConstants.SamSpendProfileAction.LINK),
        "isAuthorized")) {
      throw SpendUnauthorizedException.linkUnauthorized(spendProfileId);
    }

    SpendProfile spend;
    if (bpmEnabled) {
      spend = getSpendProfileFromBpm(userRequest, spendProfileId);
    } else {
      spend = spendProfiles.get(spendProfileId);
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

  private static List<SpendProfile> parse(
      List<SpendProfileConfiguration.SpendProfileModel> spendModels) {
    return spendModels.stream()
        .map(
            spendModel ->
                SpendProfile.builder()
                    .id(new SpendProfileId(spendModel.getId()))
                    .billingAccountId(spendModel.getBillingAccountId())
                    .build())
        .collect(Collectors.toList());
  }

  private SpendProfile getSpendProfileFromBpm(
      AuthenticatedUserRequest userRequest, SpendProfileId spendProfileId) {
    SpendProfile spend = null;
    var profileApi = bpmClientProvider.getProfileApi(userRequest);
    try {
      var profile = profileApi.getProfile(UUID.fromString(spendProfileId.getId()));

      spend =
          SpendProfile.builder()
              .id(spendProfileId)
              .billingAccountId(profile.getBillingAccountId())
              .managedResourceGroupId(profile.getManagedResourceGroupId())
              .tenantId(profile.getTenantId())
              .subscriptionId(profile.getSubscriptionId())
              .build();
    } catch (ApiException ex) {
      if (ex.getCode() == HttpStatus.NOT_FOUND.value()
          || ex.getCode() == HttpStatus.FORBIDDEN.value()) {
        return null;
      } else {
        throw new BillingProfileManagerServiceAPIException(ex);
      }
    }

    return spend;
  }
}
