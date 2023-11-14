package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@Tag("connected")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SpendProfileBpmConnectedTest extends BaseConnectedTest {
  @Autowired SamService samService;
  @Autowired SpendProfileConfiguration spendProfileConfiguration;
  @Autowired SpendConnectedTestUtils spendUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired SpendProfileService spendProfileService;

  SpendProfile profile;

  /** Condition used to disable these tests if run in a deployment where BPM is not configured. */
  public boolean bpmUnavailable() {
    return StringUtils.isEmpty(spendProfileConfiguration.getBasePath());
  }

  @BeforeAll
  public void setup() {
    // BeforeAll and AfterAll will still run even if there are no enabled tests in this class.
    if (bpmUnavailable()) {
      return;
    }
    var profileName = "wsm-test-" + UUID.randomUUID();
    var billingAcctId = spendUtils.defaultBillingAccountId();
    profile =
        spendProfileService.createGcpSpendProfile(
            billingAcctId, profileName, "direct", userAccessUtils.thirdUserAuthRequest());
  }

  @AfterAll
  public void cleanUp() {
    if (bpmUnavailable()) {
      return;
    }
    spendProfileService.deleteProfile(
        UUID.fromString(profile.id().getId()), userAccessUtils.thirdUserAuthRequest());
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void authorizeLinkingSuccess() {
    var linkedProfile =
        spendProfileService.authorizeLinking(
            profile.id(), true, userAccessUtils.thirdUserAuthRequest());
    assertEquals(linkedProfile.billingAccountId(), profile.billingAccountId());
    assertEquals(linkedProfile.id(), profile.id());
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void authorizeLinkingFailure() {
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            spendProfileService.authorizeLinking(
                profile.id(), true, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void authorizeLinkingUnknownId() {
    var ex =
        assertThrows(
            SpendUnauthorizedException.class,
            () ->
                spendProfileService.authorizeLinking(
                    new SpendProfileId(UUID.randomUUID().toString()),
                    true,
                    userAccessUtils.thirdUserAuthRequest()));
    assert (ex.getStatusCode() == HttpStatus.FORBIDDEN);
  }
}
