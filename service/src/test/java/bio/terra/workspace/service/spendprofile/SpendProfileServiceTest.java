package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.OpenTelemetry;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connected")
class SpendProfileServiceTest extends BaseConnectedTest {
  @Autowired SamService samService;
  @Autowired SpendProfileConfiguration spendProfileConfiguration;
  @Autowired SpendConnectedTestUtils spendUtils;
  @Autowired UserAccessUtils userAccessUtils;

  /** Condition used to disable these tests if run in a deployment where BPM is not configured. */
  public boolean bpmUnavailable() {
    return StringUtils.isEmpty(spendProfileConfiguration.getBasePath());
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void authorizeLinkingSuccess() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile =
        SpendProfile.buildGcpSpendProfile(id, spendUtils.defaultBillingAccountId());
    SpendProfileService service =
        new SpendProfileService(
            samService, ImmutableList.of(profile), spendProfileConfiguration, OpenTelemetry.noop());

    assertEquals(
        profile, service.authorizeLinking(id, false, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void authorizeLinkingSamUnauthorizedThrowsUnauthorized() {
    SpendProfileId id = spendUtils.defaultSpendId();
    SpendProfile profile =
        SpendProfile.buildGcpSpendProfile(new SpendProfileId("bad-profile-id"), "fake");
    SpendProfileService service =
        new SpendProfileService(
            samService, ImmutableList.of(profile), spendProfileConfiguration, OpenTelemetry.noop());

    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(id, false, userAccessUtils.secondUserAuthRequest()));
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void authorizeLinkingUnknownIdThrowsUnauthorized() {
    SpendProfileService service =
        new SpendProfileService(
            samService, ImmutableList.of(), spendProfileConfiguration, OpenTelemetry.noop());
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            service.authorizeLinking(
                new SpendProfileId("bar"), false, userAccessUtils.defaultUserAuthRequest()));
  }

  @Test
  @DisabledIf("bpmUnavailable")
  void parseSpendProfileConfiguration() {
    SpendProfileService service =
        new SpendProfileService(samService, spendProfileConfiguration, OpenTelemetry.noop());
    assertEquals(
        SpendProfile.buildGcpSpendProfile(
            spendUtils.defaultSpendId(), spendUtils.defaultBillingAccountId()),
        service.authorizeLinking(
            spendUtils.defaultSpendId(), false, userAccessUtils.defaultUserAuthRequest()));
  }
}
