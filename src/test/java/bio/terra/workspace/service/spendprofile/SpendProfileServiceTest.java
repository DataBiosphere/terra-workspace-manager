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

public class SpendProfileServiceTest extends BaseConnectedTest {
  @Autowired SamService samService;
  @Autowired SpendProfileConfiguration spendProfileConfiguration;
  @Autowired SpendConnectedTestUtils spendUtils;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  public void authorizeLinkingSuccess() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service = new SpendProfileService(samService, ImmutableList.of(profile));

    assertEquals(profile, service.authorizeLinking(id, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  public void authorizeLinkingSamUnauthorizedThrowsUnauthorized() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service = new SpendProfileService(samService, ImmutableList.of(profile));

    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(id, userAccessUtils.secondUserAuthRequest()));
  }

  @Test
  public void authorizeLinkingUnknownIdThrowsUnauthorized() {
    SpendProfileService service = new SpendProfileService(samService, ImmutableList.of());
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            service.authorizeLinking(
                SpendProfileId.create("bar"), userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  public void parseSpendProfileConfiguration() {
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
