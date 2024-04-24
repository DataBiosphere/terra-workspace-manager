package bio.terra.workspace.service.spendprofile;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utilities for working with spend profiles in connected tests. */
@Component
public class SpendConnectedTestUtils {

  @Autowired private SpendProfileConfiguration spendConfig;

  private SpendProfile defaultSpendProfile;

  /**
   * Returns a {@link SpendProfile} that can be used to link to spend on cloud contexts.
   *
   * <p>Note though that this SpendProfile will not work with an Azure CloudContext unless the call
   * to BPM to obtain the spend profile is mocked. For an example, see
   * BaseAzureConnectedTest.initSpendProfileMock.
   */
  public SpendProfile defaultGcpSpendProfile() {
    if (defaultSpendProfile == null) {
      SpendProfileConfiguration.SpendProfileModel defaultModel =
          spendConfig.getSpendProfiles().get(0);
      defaultSpendProfile =
          SpendProfile.buildGcpSpendProfile(
              new SpendProfileId(defaultModel.getId()), defaultModel.getBillingAccountId());
    }
    return defaultSpendProfile;
  }

  /**
   * Returns a {@link SpendProfileId} that can be used to link to spend on workspaces.
   *
   * <p>Note though that this SpendProfileId will not work with an Azure CloudContext unless the
   * call to BPM to obtain the spend profile is mocked. For an example, see
   * BaseAzureConnectedTest.initSpendProfileMock.
   */
  public SpendProfileId defaultSpendId() {
    return defaultGcpSpendProfile().id();
  }

  /** Returns a billing account id that can be used for billing projects on workspaces. */
  public String defaultBillingAccountId() {
    return defaultGcpSpendProfile().billingAccountId();
  }
}
