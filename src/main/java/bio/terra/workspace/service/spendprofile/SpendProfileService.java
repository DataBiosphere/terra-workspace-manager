package bio.terra.workspace.service.spendprofile;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.SamUtils;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
  private final Map<SpendProfileId, SpendProfile> spendProfiles;

  @Autowired
  public SpendProfileService(
      SamService samService, SpendProfileConfiguration spendProfileConfiguration) {
    this(samService, parse(spendProfileConfiguration.getSpendProfiles()));
  }

  public SpendProfileService(SamService samService, List<SpendProfile> spendProfiles) {
    this.samService = samService;
    this.spendProfiles = Maps.uniqueIndex(spendProfiles, SpendProfile::id);
  }

  /**
   * Authorize the user to link the Spend Profile. Returns the {@link SpendProfile} associated with
   * the id if there is one and the user is authorized to link it. Otherwise, throws a {@link
   * SpendUnauthorizedException}.
   */
  public SpendProfile authorizeLinking(
      SpendProfileId spendProfileId, AuthenticatedUserRequest userRequest) {
    if (!samService.isAuthorized(
        userRequest.getRequiredToken(),
        SamUtils.SPEND_PROFILE_RESOURCE,
        spendProfileId.id(),
        SamUtils.SPEND_PROFILE_LINK_ACTION)) {
      throw SpendUnauthorizedException.linkUnauthorized(spendProfileId);
    }
    SpendProfile spend = spendProfiles.get(spendProfileId);
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
                    .id(SpendProfileId.create(spendModel.getId()))
                    .billingAccountId(spendModel.getBillingAccountId())
                    .build())
        .collect(Collectors.toList());
  }
}
