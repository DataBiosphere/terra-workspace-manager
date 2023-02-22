package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;

public class PolicyFixtures {
  public static final String NAMESPACE = "terra";
  public static final String GROUP_CONSTRAINT = "group-constraint";
  public static final String REGION_CONSTRAINT = "region-constraint";
  public static final String GROUP = "group";
  public static final String REGION = "region-name";
  public static final String DDGROUP = "ddgroup";
  public static final String US_REGION = "usa";

  public static ApiWsmPolicyInput GROUP_POLICY =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(GROUP_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(GROUP).value(DDGROUP));

  public static ApiWsmPolicyInput REGION_POLICY =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(REGION).value(US_REGION));
}
