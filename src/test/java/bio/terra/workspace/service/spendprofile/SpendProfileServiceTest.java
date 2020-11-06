package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;

// TODO(PF-186): Consider if this should be a connected test that talks to Sam.
public class SpendProfileServiceTest extends BaseUnitTest {
  @MockBean SamService mockSamService;

  /** Fake user access token. */
  private static final String USER_ACCESS_TOKEN = "fake-token";

  @BeforeEach
  public void setUp() {
    // By default, allow the user access token to link spend profile resources for any id.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.eq(USER_ACCESS_TOKEN),
                Mockito.eq(SamUtils.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamUtils.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(true);
  }

  @Test
  public void authorizeLinking_Success() {
    SpendProfileId id = SpendProfileId.create("foo");
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service =
        new SpendProfileService(mockSamService, ImmutableList.of(profile));

    assertEquals(profile, service.authorizeLinking(id, USER_ACCESS_TOKEN));
  }

  @Test
  public void authorizeLinking_SamUnauthorized_ThrowsUnauthorized() {
    SpendProfileId id = SpendProfileId.create("foo");
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service =
        new SpendProfileService(mockSamService, ImmutableList.of(profile));

    Mockito.when(
            mockSamService.isAuthorized(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(false);
    assertThrows(
        SpendUnauthorizedException.class, () -> service.authorizeLinking(id, USER_ACCESS_TOKEN));
  }

  @Test
  public void authorizeLinking_UnknownId_ThrowsUnauthorized() {
    SpendProfileService service = new SpendProfileService(mockSamService, ImmutableList.of());
    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(SpendProfileId.create("bar"), USER_ACCESS_TOKEN));
  }

  @Test
  public void parseSpendProfileConfiguration() {
    SpendProfileConfiguration.SpendProfileModel idOnlyModel =
        new SpendProfileConfiguration.SpendProfileModel();
    idOnlyModel.setId("foo");
    SpendProfileConfiguration.SpendProfileModel idAndBillingModel =
        new SpendProfileConfiguration.SpendProfileModel();
    idAndBillingModel.setId("bar");
    idAndBillingModel.setBillingAccountId("fake-billing-account");

    SpendProfileConfiguration configuration = new SpendProfileConfiguration();
    configuration.setSpendProfiles(ImmutableList.of(idOnlyModel, idAndBillingModel));
    SpendProfileService service = new SpendProfileService(mockSamService, configuration);

    SpendProfileId fooId = SpendProfileId.create("foo");
    assertEquals(
        SpendProfile.builder().id(fooId).build(),
        service.authorizeLinking(fooId, USER_ACCESS_TOKEN));
    SpendProfileId barId = SpendProfileId.create("bar");
    assertEquals(
        SpendProfile.builder().id(barId).billingAccountId("fake-billing-account").build(),
        service.authorizeLinking(barId, USER_ACCESS_TOKEN));
  }
}
