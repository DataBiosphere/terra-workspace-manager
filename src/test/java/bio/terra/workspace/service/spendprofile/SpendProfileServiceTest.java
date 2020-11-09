package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;

// TODO(PF-186): Consider if this should be a connected test that talks to Sam.
public class SpendProfileServiceTest extends BaseUnitTest {
  @MockBean SamService mockSamService;

  /** Fake user request with access token. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest().token(Optional.of("fake-token"));

  @BeforeEach
  public void setUp() {
    // By default, allow the user access token to link spend profile resources for any id.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.eq(USER_REQUEST.getRequiredToken()),
                Mockito.eq(SamUtils.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamUtils.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(true);
  }

  @Test
  public void authorizeLinkingSuccess() {
    SpendProfileId id = SpendProfileId.create("foo");
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service =
        new SpendProfileService(mockSamService, ImmutableList.of(profile));

    assertEquals(profile, service.authorizeLinking(id, USER_REQUEST));
  }

  @Test
  public void authorizeLinkingSamUnauthorizedThrowsUnauthorized() {
    SpendProfileId id = SpendProfileId.create("foo");
    SpendProfile profile = SpendProfile.builder().id(id).build();
    SpendProfileService service =
        new SpendProfileService(mockSamService, ImmutableList.of(profile));

    Mockito.when(
            mockSamService.isAuthorized(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(false);
    assertThrows(
        SpendUnauthorizedException.class, () -> service.authorizeLinking(id, USER_REQUEST));
  }

  @Test
  public void authorizeLinkingUnknownIdThrowsUnauthorized() {
    SpendProfileService service = new SpendProfileService(mockSamService, ImmutableList.of());
    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(SpendProfileId.create("bar"), USER_REQUEST));
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
        SpendProfile.builder().id(fooId).build(), service.authorizeLinking(fooId, USER_REQUEST));
    SpendProfileId barId = SpendProfileId.create("bar");
    assertEquals(
        SpendProfile.builder().id(barId).billingAccountId("fake-billing-account").build(),
        service.authorizeLinking(barId, USER_REQUEST));
  }
}
