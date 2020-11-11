package bio.terra.workspace.service.spendprofile;

import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utiliteis for working with spend profiles in connected tests. */
@Component
public class SpendConnectedTestUtils {

  @Autowired private SpendProfileConfiguration spendConfig;

  /** Returns a {@link SpendProfileId} that can be used to link to spend on workspaces. */
  public SpendProfileId defaultSpendId() {
    return SpendProfileId.create(spendConfig.getSpendProfiles().get(0).getId());
  }

  /** Returns a billing account id that can be used for billing projects on workspaces. */
  public String defaultBillingAccountId() {
    return spendConfig.getSpendProfiles().get(0).getBillingAccountId();
  }

  /** Returns a SpendProfileId in the spend profile service with no billing account associated. */
  public SpendProfileId noBillingAccount() {
    return SpendProfileId.create("no-billing-account");
  }
}
