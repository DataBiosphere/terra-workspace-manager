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
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.spendprofile.client.BpmClientProvider;
import bio.terra.workspace.service.spendprofile.exceptions.BillingProfileManagerServiceAPIException;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SpendProfileServiceTest extends BaseUnitTest {
  AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest(
          "example@example.com", "fake-subject-id", Optional.of("fake-token"));

  @Test
  void authorizeLinkingSuccess() throws InterruptedException {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(false);
    var id = new SpendProfileId(UUID.randomUUID().toString());
    SpendProfile profile = SpendProfile.builder().id(id).build();
    when(mockSamService().isAuthorized(any(), any(), any(), any())).thenReturn(true);
    SpendProfileService service =
        new SpendProfileService(
            mockSamService(), ImmutableList.of(profile), bpmClientProvider, featureConfiguration);

    assertEquals(profile, service.authorizeLinking(id, userRequest));
  }

  @Test
  void authorizeLinkingSamUnauthorizedThrowsUnauthorized() throws InterruptedException {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(false);
    var id = new SpendProfileId(UUID.randomUUID().toString());
    SpendProfile profile = SpendProfile.builder().id(id).build();
    when(mockSamService().isAuthorized(any(), any(), any(), any())).thenReturn(false);
    SpendProfileService service =
        new SpendProfileService(
            mockSamService(), ImmutableList.of(profile), bpmClientProvider, featureConfiguration);

    assertThrows(SpendUnauthorizedException.class, () -> service.authorizeLinking(id, userRequest));
  }

  @Test
  void authorizeLinkingUnknownIdThrowsUnauthorized() {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(false);
    SpendProfileService service =
        new SpendProfileService(
            mockSamService(), ImmutableList.of(), bpmClientProvider, featureConfiguration);
    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(new SpendProfileId("bar"), userRequest));
  }

  @Test
  void adaptConfigurationModels() throws InterruptedException {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(false);
    var spendProfileModel = new SpendProfileConfiguration.SpendProfileModel();
    var id = UUID.randomUUID().toString();
    spendProfileModel.setId(id);
    spendProfileModel.setBillingAccountId("fake-account-id");
    var config = new SpendProfileConfiguration();
    config.setSpendProfiles(Collections.singletonList(spendProfileModel));

    SpendProfileService service =
        new SpendProfileService(mockSamService(), config, bpmClientProvider, featureConfiguration);
    when(mockSamService().isAuthorized(any(), any(), any(), any())).thenReturn(true);

    assertEquals(
        SpendProfile.builder()
            .id(new SpendProfileId(id))
            .billingAccountId("fake-account-id")
            .build(),
        service.authorizeLinking(new SpendProfileId(id), userRequest));
  }

  @Test
  void authorizeLinkingSuccessWhenBillingProfileExists() throws Exception {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(true);
    var profileApi = mock(ProfileApi.class);
    when(bpmClientProvider.getProfileApi(any())).thenReturn(profileApi);
    when(mockSamService().isAuthorized(any(), any(), any(), any())).thenReturn(true);

    var profileId = UUID.randomUUID();
    when(profileApi.getProfile(any()))
        .thenReturn(new ProfileModel().billingAccountId("fake-billing-account-id").id(profileId));

    SpendProfileService spendProfileService =
        new SpendProfileService(
            mockSamService(), Collections.emptyList(), bpmClientProvider, featureConfiguration);

    var spend =
        spendProfileService.authorizeLinking(new SpendProfileId(profileId.toString()), userRequest);

    assertEquals(
        SpendProfile.builder()
            .billingAccountId("fake-billing-account-id")
            .id(new SpendProfileId(profileId.toString()))
            .build(),
        spend);
  }

  @Test
  void authorizeLinkingFailureWhenProfileDoesNotExist() throws Exception {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();
    featureConfiguration.setBpmEnabled(true);

    var profileApi = mock(ProfileApi.class);
    when(bpmClientProvider.getProfileApi(eq(userRequest))).thenReturn(profileApi);
    when(profileApi.getProfile(any()))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "missing"));

    var spendProfileId = new SpendProfileId(UUID.randomUUID().toString());
    when(mockSamService()
            .isAuthorized(
                eq(userRequest),
                eq(SamConstants.SamResource.SPEND_PROFILE),
                eq(spendProfileId.getId()),
                eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    SpendProfileService spendProfileService =
        new SpendProfileService(
            mockSamService(), Collections.emptyList(), bpmClientProvider, featureConfiguration);

    assertThrows(
        SpendUnauthorizedException.class,
        () -> spendProfileService.authorizeLinking(spendProfileId, userRequest));
  }

  @Test
  void authorizeLinkingFailureWhenBpmDies() throws Exception {
    var bpmClientProvider = mock(BpmClientProvider.class);
    FeatureConfiguration featureConfiguration = new FeatureConfiguration();

    featureConfiguration.setBpmEnabled(true);

    var profileApi = mock(ProfileApi.class);
    when(bpmClientProvider.getProfileApi(eq(userRequest))).thenReturn(profileApi);

    when(profileApi.getProfile(any()))
        .thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "missing"));

    var spendProfileId = new SpendProfileId(UUID.randomUUID().toString());
    when(mockSamService()
            .isAuthorized(
                eq(userRequest),
                eq(SamConstants.SamResource.SPEND_PROFILE),
                eq(spendProfileId.getId()),
                eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    SpendProfileService spendProfileService =
        new SpendProfileService(
            mockSamService(), Collections.emptyList(), bpmClientProvider, featureConfiguration);

    assertThrows(
        BillingProfileManagerServiceAPIException.class,
        () -> spendProfileService.authorizeLinking(spendProfileId, userRequest));
  }
}
