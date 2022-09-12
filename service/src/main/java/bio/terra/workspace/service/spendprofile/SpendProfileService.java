package bio.terra.workspace.service.spendprofile;

import bio.terra.common.exception.ValidationException;
import bio.terra.profile.api.ProfileApi;
import bio.terra.profile.client.ApiClient;
import bio.terra.profile.client.ApiException;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  // private final Map<SpendProfileId, SpendProfile> spendProfiles;
  private final SpendProfileConfiguration spendProfileConfiguration;
  private final Client commonHttpClient;

  @Autowired
  public SpendProfileService(SamService samService, SpendProfileConfiguration spendProfileConfiguration) {
    this.samService = samService;
    this.spendProfileConfiguration = spendProfileConfiguration;
    commonHttpClient = new bio.terra.datarepo.client.ApiClient().getHttpClient();
  }


  private ApiClient getApiClient(String accessToken) {
    var client = new ApiClient().setHttpClient(commonHttpClient);
    client.setAccessToken(accessToken);
    client.setBasePath(spendProfileConfiguration.getBasePath());
    return client;
  }

  private ProfileApi getProfileApi(AuthenticatedUserRequest userRequest) {
    return new ProfileApi(getApiClient(userRequest.getRequiredToken()));
  }

  private Map<SpendProfileId, SpendProfile> getProfiles(AuthenticatedUserRequest userRequest) {
    var profileApi = getProfileApi(userRequest);
    try {
      return profileApi.listProfiles(0, 1000).getItems().stream()
          .map(
              profile ->
                  SpendProfile.builder()
                      .billingAccountId(profile.getBillingAccountId())
                      .id(new SpendProfileId(profile.getId().toString()))
                      .build())
          .collect(Collectors.toMap(SpendProfile::id, Function.identity()));

    } catch (ApiException e) {
      throw new RuntimeException("whoops", e);
    }
  }

  /**
   * Authorize the user to link the Spend Profile. Returns the {@link SpendProfile} associated with
   * the id if there is one and the user is authorized to link it. Otherwise, throws a {@link
   * SpendUnauthorizedException}.
   */
  public SpendProfile authorizeLinking(
      SpendProfileId spendProfileId, AuthenticatedUserRequest userRequest) {
    // TODO factor this into a BPM call.
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

    SpendProfile spend = getProfiles(userRequest).get(spendProfileId);
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
}
