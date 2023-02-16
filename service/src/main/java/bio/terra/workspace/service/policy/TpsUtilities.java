package bio.terra.workspace.service.policy;

import bio.terra.policy.model.TpsPolicyInputs;
import java.util.ArrayList;
import java.util.List;

public class TpsUtilities {
  public static final String TERRA_NAMESPACE = "terra";
  public static final String GROUP_CONSTRAINT = "group-constraint";

  public static List<String> getGroupConstraintsFromInputs(TpsPolicyInputs inputs) {
    List<String> result = new ArrayList<>();

    for (var input : inputs.getInputs()) {
      if (input.getNamespace() == TERRA_NAMESPACE && input.getName() == GROUP_CONSTRAINT) {
        for (var data : input.getAdditionalData()) {
          result.add(data.getValue());
        }
      }
    }

    return result;
  }
}
