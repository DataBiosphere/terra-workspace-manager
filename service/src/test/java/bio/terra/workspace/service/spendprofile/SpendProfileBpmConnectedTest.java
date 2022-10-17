package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.profile.model.CloudPlatform;
import bio.terra.profile.model.CreateProfileRequest;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SpendProfileBpmConnectedTest extends BaseConnectedTest {
  @Autowired SamService samService;
  @Autowired SpendProfileConfiguration spendProfileConfiguration;
  @Autowired SpendConnectedTestUtils spendUtils;
  @Autowired UserAccessUtils userAccessUtils;

  SpendProfile profile;

  @BeforeAll
  public void setup() {
    SpendProfileService svc = new SpendProfileService(samService, spendProfileConfiguration);
    var profileName = "wsm-test-" + UUID.randomUUID();
    var billingAcctId = spendUtils.defaultBillingAccountId();
    CreateProfileRequest req =
        new CreateProfileRequest()
            .id(UUID.randomUUID())
            .billingAccountId(billingAcctId)
            .displayName(profileName)
            .biller("direct")
            .cloudPlatform(CloudPlatform.GCP);
    profile =
        svc.createGcpSpendProfile(
            billingAcctId, profileName, "direct", userAccessUtils.thirdUserAuthRequest());
  }

  @AfterAll
  public void cleanUp() {
    SpendProfileService svc = new SpendProfileService(samService, spendProfileConfiguration);
    svc.deleteProfile(
        UUID.fromString(profile.getId().getId()), userAccessUtils.thirdUserAuthRequest());
    System.out.println("cleaned up!");
  }

  @Test
  void authorizeLinkingSuccess() {
    SpendProfileService svc = new SpendProfileService(samService, spendProfileConfiguration);
    var linkedProfile =
        svc.authorizeLinking(profile.getId(), true, userAccessUtils.thirdUserAuthRequest());
    assertEquals(linkedProfile.getBillingAccountId(), profile.getBillingAccountId());
    assertEquals(linkedProfile.getId(), profile.getId());
  }

  @Test
  void authorizeLinkingFailure() {
    SpendProfileService svc = new SpendProfileService(samService, spendProfileConfiguration);
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            svc.authorizeLinking(profile.getId(), true, userAccessUtils.defaultUserAuthRequest()));
  }
}
