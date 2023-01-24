package bio.terra.workspace.service.spendprofile;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utilities for working with spend profiles in connected tests. */
@Component
public class SpendConnectedTestUtils {

  @Autowired private SpendProfileConfiguration spendConfig;

  /**
   * Returns a {@link SpendProfileId} that can be used to link to spend on workspaces.
   *
   * <p>Note though that this SpendProfileId will not work with an Azure CloudContext unless the
   * call to BPM to obtain the spend profile is mocked. For an example, see
   * BaseAzureConnectedTest.initSpendProfileMock.
   */
  public SpendProfileId defaultSpendId() {
    return new SpendProfileId(spendConfig.getSpendProfiles().get(0).getId());
  }

  /** Returns a billing account id that can be used for billing projects on workspaces. */
  public String defaultBillingAccountId() {
    return spendConfig.getSpendProfiles().get(0).getBillingAccountId();
  }

  /** Returns a SpendProfileId in the spend profile service with no billing account associated. */
  public SpendProfileId noBillingAccount() {
    return new SpendProfileId("no-billing-account");
  }
}
