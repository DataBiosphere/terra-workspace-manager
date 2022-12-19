package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;

public class PolicyFixtures {
  public static final String NAMESPACE = "terra";
  public static final String GROUP_CONSTRAINT = "group-constraint";
  public static final String REGION_CONSTRAINT = "region-constraint";
  public static final String GROUP = "group";
  public static final String REGION = "region";
  public static final String DDGROUP = "ddgroup";
  public static final String US_REGION = "US";

  public static ApiTpsPolicyInput GROUP_POLICY =
      new ApiTpsPolicyInput()
          .namespace(NAMESPACE)
          .name(GROUP_CONSTRAINT)
          .addAdditionalDataItem(new ApiTpsPolicyPair().key(GROUP).value(DDGROUP));

  public static ApiTpsPolicyInput REGION_POLICY =
      new ApiTpsPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiTpsPolicyPair().key(REGION).value(US_REGION));
}
