package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;

public class PolicyFixtures {
  public static final String NAMESPACE = "terra";
  public static final String GROUP_CONSTRAINT = "group-constraint";
  public static final String REGION_CONSTRAINT = "region-constraint";
  public static final String GROUP = "group";
  public static final String DEFAULT_GROUP = "wsm-test-group";
  public static final String ALT_GROUP = "wsm-test-group-alt";
  public static final String REGION = "region-name";
  public static final String US_REGION = "usa";
  public static final String IOWA_REGION = "iowa";
  public static final String NEVADA_REGION = "nevada";

  public static ApiWsmPolicyInput GROUP_POLICY_DEFAULT =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(GROUP_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(GROUP).value(DEFAULT_GROUP));
  public static ApiWsmPolicyInput GROUP_POLICY_ALT =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(GROUP_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(GROUP).value(ALT_GROUP));
  public static ApiWsmPolicyInput REGION_POLICY_USA =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(REGION).value(US_REGION));

  public static final ApiWsmPolicyInputs DEFAULT_WSM_POLICY_INPUTS =
      new ApiWsmPolicyInputs().addInputsItem(GROUP_POLICY_DEFAULT).addInputsItem(REGION_POLICY_USA);

  public static ApiWsmPolicyInput REGION_POLICY_IOWA =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(REGION).value(IOWA_REGION));

  public static ApiWsmPolicyInput REGION_POLICY_NEVADA =
      new ApiWsmPolicyInput()
          .namespace(NAMESPACE)
          .name(REGION_CONSTRAINT)
          .addAdditionalDataItem(new ApiWsmPolicyPair().key(REGION).value(NEVADA_REGION));
}
