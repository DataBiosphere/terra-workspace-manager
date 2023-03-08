package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;

public class PolicyFixtures {
  public static final String NAMESPACE = "terra";
  public static final String REGION_CONSTRAINT = "region-constraint";
  public static final String REGION = "region-name";
  public static final String US_REGION = "usa";
  public static final String IOWA_REGION = "iowa";

  public static ApiWsmPolicyInput REGION_POLICY_USA =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(REGION).value(US_REGION));

  public static ApiWsmPolicyInput REGION_POLICY_IOWA =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(REGION).value(IOWA_REGION));
}
