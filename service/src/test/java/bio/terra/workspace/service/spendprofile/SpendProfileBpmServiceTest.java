package bio.terra.workspace.service.spendprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.profile.api.ProfileApi;
import bio.terra.profile.client.ApiException;
import bio.terra.profile.model.ProfileModel;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.spendprofile.client.BpmClientProvider;
import bio.terra.workspace.service.spendprofile.exceptions.BillingProfileManagerServiceAPIException;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

// TODO refactor / cleanup
public class SpendProfileBpmServiceTest extends BaseUnitTest {

  AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest("example@example.com", "fake-sub", Optional.of("fake-token"));

  @Test
  void authorizeLinkingSuccessWhenBillingProfileExists() throws Exception {
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(true);
    var samService = mock(SamService.class);
    var bpmClientProvider = mock(BpmClientProvider.class);

    var profileApi = mock(ProfileApi.class);
    when(bpmClientProvider.getProfileApi(eq(userRequest))).thenReturn(profileApi);

    var billingAccountId = "fake_billing_account_id";
    when(profileApi.getProfile(any()))
        .thenReturn(new ProfileModel().billingAccountId(billingAccountId));

    var spendProfileId = new SpendProfileId(UUID.randomUUID().toString());
    when(samService.isAuthorized(
            eq(userRequest),
            eq(SamConstants.SamResource.SPEND_PROFILE),
            eq(spendProfileId.getId()),
            eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    SpendProfileService spendProfileService =
        new SpendProfileService(
            samService, Collections.emptyList(), bpmClientProvider, featureConfiguration);

    var spend = spendProfileService.authorizeLinking(spendProfileId, userRequest);

    assertEquals(spend.billingAccountId().get(), billingAccountId);
  }

  @Test
  void authorizeLinkingFailureWhenProfileDoesNotExist() throws Exception {
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(true);
    var samService = mock(SamService.class);
    var bpmClientProvider = mock(BpmClientProvider.class);

    var profileApi = mock(ProfileApi.class);
    when(bpmClientProvider.getProfileApi(eq(userRequest))).thenReturn(profileApi);

    when(profileApi.getProfile(any()))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "missing"));

    var spendProfileId = new SpendProfileId(UUID.randomUUID().toString());
    when(samService.isAuthorized(
            eq(userRequest),
            eq(SamConstants.SamResource.SPEND_PROFILE),
            eq(spendProfileId.getId()),
            eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    SpendProfileService spendProfileService =
        new SpendProfileService(
            samService, Collections.emptyList(), bpmClientProvider, featureConfiguration);

    assertThrows(
        SpendUnauthorizedException.class,
        () -> spendProfileService.authorizeLinking(spendProfileId, userRequest));
  }

  @Test
  void authorizeLinkingFailureWhenBpmDies() throws Exception {
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(true);
    var samService = mock(SamService.class);
    var bpmClientProvider = mock(BpmClientProvider.class);

    var profileApi = mock(ProfileApi.class);
    when(bpmClientProvider.getProfileApi(eq(userRequest))).thenReturn(profileApi);

    when(profileApi.getProfile(any()))
        .thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "missing"));

    var spendProfileId = new SpendProfileId(UUID.randomUUID().toString());
    when(samService.isAuthorized(
            eq(userRequest),
            eq(SamConstants.SamResource.SPEND_PROFILE),
            eq(spendProfileId.getId()),
            eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    SpendProfileService spendProfileService =
        new SpendProfileService(
            samService, Collections.emptyList(), bpmClientProvider, featureConfiguration);

    assertThrows(
        BillingProfileManagerServiceAPIException.class,
        () -> spendProfileService.authorizeLinking(spendProfileId, userRequest));
  }
}
