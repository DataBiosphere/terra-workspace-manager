package bio.terra.workspace.service.policy;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPolicyInputs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TpsUtilities {
  public static final String TERRA_NAMESPACE = "terra";
  public static final String GROUP_CONSTRAINT = "group-constraint";
  public static final String GROUP_KEY = "group";
  public static final String PROTECTED_DATA_POLICY_NAME = "protected-data";
  public static final String DATA_TRACKING_POLICY_NAME = "data-tracking";

  public static List<String> getGroupConstraintsFromInputs(TpsPolicyInputs inputs) {
    List<String> result = new ArrayList<>();

    if (inputs == null) {
      return result;
    }

    for (var input : inputs.getInputs()) {
      if (input.getNamespace().equals(TERRA_NAMESPACE)
          && input.getName().equals(GROUP_CONSTRAINT)) {
        for (var data : input.getAdditionalData()) {
          if (data.getKey().equals(GROUP_KEY)) {
            result.add(data.getValue());
          }
        }
      }
    }

    return result;
  }

  public static HashSet<String> getAddedGroups(
      TpsPaoGetResult originalPolicies, TpsPaoGetResult updatedPolicies) {
    HashSet<String> originalGroups =
        originalPolicies == null
            ? new HashSet<>()
            : new HashSet<>(
                TpsUtilities.getGroupConstraintsFromInputs(
                    originalPolicies.getEffectiveAttributes()));

    HashSet<String> updatedGroups =
        updatedPolicies == null
            ? new HashSet<>()
            : new HashSet<>(
                TpsUtilities.getGroupConstraintsFromInputs(
                    updatedPolicies.getEffectiveAttributes()));

    updatedGroups.removeAll(originalGroups);
    return updatedGroups;
  }

  public static HashSet<String> getRemovedGroups(
      TpsPaoGetResult originalPolicies, TpsPaoGetResult updatedPolicies) {
    HashSet<String> originalGroups =
        originalPolicies == null
            ? new HashSet<>()
            : new HashSet<>(
                TpsUtilities.getGroupConstraintsFromInputs(
                    originalPolicies.getEffectiveAttributes()));

    HashSet<String> updatedGroups =
        updatedPolicies == null
            ? new HashSet<>()
            : new HashSet<>(
                TpsUtilities.getGroupConstraintsFromInputs(
                    updatedPolicies.getEffectiveAttributes()));

    originalGroups.removeAll(updatedGroups);
    return originalGroups;
  }

  public static boolean containsProtectedDataPolicy(TpsPolicyInputs inputs) {
    if (inputs == null) return false;
    return inputs.getInputs().stream()
        .anyMatch(
            input ->
                input.getNamespace().equals(TERRA_NAMESPACE)
                    && input.getName().equals(PROTECTED_DATA_POLICY_NAME));
  }

  public static boolean containsDataTrackingPolicy(TpsPolicyInputs inputs) {
    if (inputs == null) return false;
    return inputs.getInputs().stream()
        .anyMatch(
            input ->
                input.getNamespace().equals(TERRA_NAMESPACE)
                    && input.getName().equals(DATA_TRACKING_POLICY_NAME));
  }
}
