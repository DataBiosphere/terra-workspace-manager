package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SpendProfileServiceTest extends BaseConnectedTest {
  @Autowired SamService samService;
  @Autowired SpendProfileConfiguration spendProfileConfiguration;
  @Autowired SpendConnectedTestUtils spendUtils;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  void authorizeLinkingSuccess() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile =
        SpendProfile.buildGcpSpendProfile(id, spendUtils.defaultBillingAccountId());
    SpendProfileService service =
        new SpendProfileService(samService, ImmutableList.of(profile), spendProfileConfiguration);

    assertEquals(
        profile, service.authorizeLinking(id, false, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  void authorizeLinkingSamUnauthorizedThrowsUnauthorized() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile =
        SpendProfile.buildGcpSpendProfile(new SpendProfileId("bad-profile-id"), "fake");
    SpendProfileService service =
        new SpendProfileService(samService, ImmutableList.of(profile), spendProfileConfiguration);

    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(id, false, userAccessUtils.secondUserAuthRequest()));
  }

  @Test
  void authorizeLinkingUnknownIdThrowsUnauthorized() {
    SpendProfileService service =
        new SpendProfileService(samService, ImmutableList.of(), spendProfileConfiguration);
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            service.authorizeLinking(
                new SpendProfileId("bar"), false, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  void parseSpendProfileConfiguration() {
    SpendProfileService service = new SpendProfileService(samService, spendProfileConfiguration);
    assertEquals(
        SpendProfile.buildGcpSpendProfile(
            spendUtils.defaultSpendId(), spendUtils.defaultBillingAccountId()),
        service.authorizeLinking(
            spendUtils.defaultSpendId(), false, userAccessUtils.defaultUserAuthRequest()));
  }
}
