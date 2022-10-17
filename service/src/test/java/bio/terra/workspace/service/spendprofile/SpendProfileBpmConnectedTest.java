package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.BillingProfileManagerServiceAPIException;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SpendProfileBpmConnectedTest extends BaseConnectedTest {
  @Autowired SamService samService;
  @Autowired SpendProfileConfiguration spendProfileConfiguration;
  @Autowired SpendConnectedTestUtils spendUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired SpendProfileService spendProfileService;

  SpendProfile profile;

  @BeforeAll
  public void setup() {
    var profileName = "wsm-test-" + UUID.randomUUID();
    var billingAcctId = spendUtils.defaultBillingAccountId();
    profile =
        spendProfileService.createGcpSpendProfile(
            billingAcctId, profileName, "direct", userAccessUtils.thirdUserAuthRequest());
  }

  @AfterAll
  public void cleanUp() {
    spendProfileService.deleteProfile(
        UUID.fromString(profile.id().getId()), userAccessUtils.thirdUserAuthRequest());
  }

  @Test
  void authorizeLinkingSuccess() {
    var linkedProfile =
        spendProfileService.authorizeLinking(
            profile.id(), true, userAccessUtils.thirdUserAuthRequest());
    assertEquals(linkedProfile.billingAccountId(), profile.billingAccountId());
    assertEquals(linkedProfile.id(), profile.id());
  }

  @Test
  void authorizeLinkingFailure() {
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            spendProfileService.authorizeLinking(
                profile.id(), true, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  void authorizeLinkingUnknownId() {
    var ex =
        assertThrows(
            BillingProfileManagerServiceAPIException.class,
            () ->
                spendProfileService.authorizeLinking(
                    new SpendProfileId(UUID.randomUUID().toString()),
                    true,
                    userAccessUtils.thirdUserAuthRequest()));
    assert (ex.getStatusCode() == HttpStatus.NOT_FOUND);
  }
}
