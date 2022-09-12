package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
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
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service = new SpendProfileService(samService, null);

    assertEquals(profile, service.authorizeLinking(id, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  void authorizeLinkingSamUnauthorizedThrowsUnauthorized() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service = new SpendProfileService(samService, null);

    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(id, userAccessUtils.secondUserAuthRequest()));
  }

  @Test
  void authorizeLinkingUnknownIdThrowsUnauthorized() {
    SpendProfileService service = new SpendProfileService(samService, null);
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            service.authorizeLinking(
                new SpendProfileId("bar"), userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  void parseSpendProfileConfiguration() {
    SpendProfileService service = new SpendProfileService(samService, spendProfileConfiguration);
    assertEquals(
        SpendProfile.builder()
            .id(spendUtils.defaultSpendId())
            .billingAccountId(spendUtils.defaultBillingAccountId())
            .build(),
        service.authorizeLinking(
            spendUtils.defaultSpendId(), userAccessUtils.defaultUserAuthRequest()));
  }
}
