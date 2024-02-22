package bio.terra.workspace.service.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TpsUtilitiesTest extends BaseSpringBootUnitTest {

  @Test
  void testGetGroupConstraintsFromInputs() {
    var testGroup = "myGroup";
    var results =
        TpsUtilities.getGroupConstraintsFromInputs(
            new TpsPolicyInputs()
                .addInputsItem(
                    new TpsPolicyInput()
                        .namespace(TpsUtilities.TERRA_NAMESPACE)
                        .name(TpsUtilities.GROUP_CONSTRAINT)
                        .addAdditionalDataItem(
                            new TpsPolicyPair().key(TpsUtilities.GROUP_KEY).value(testGroup)))
                .addInputsItem(
                    new TpsPolicyInput()
                        .namespace("not terra")
                        .name(TpsUtilities.GROUP_CONSTRAINT)
                        .addAdditionalDataItem(
                            new TpsPolicyPair().key(TpsUtilities.GROUP_KEY).value("other"))));

    assertIterableEquals(List.of(testGroup), results);
  }

  @Test
  void testGetGroupConstraintsFromInputs_empty() {
    var testGroup = "myGroup";
    var results =
        TpsUtilities.getGroupConstraintsFromInputs(
            new TpsPolicyInputs()
                .addInputsItem(
                    new TpsPolicyInput()
                        .namespace("not terra")
                        .name(TpsUtilities.GROUP_CONSTRAINT)
                        .addAdditionalDataItem(
                            new TpsPolicyPair().key(TpsUtilities.GROUP_KEY).value(testGroup))));

    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetGroupConstraintsFromInputs_null() {
    var results = TpsUtilities.getGroupConstraintsFromInputs(null);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetAddedGroups_noGroupPolicies() {
    TpsPaoGetResult inputs =
        new TpsPaoGetResult()
            .effectiveAttributes(new TpsPolicyInputs().addInputsItem(createProtectedPolicyInput()));
    HashSet<String> results = TpsUtilities.getAddedGroups(inputs, inputs);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetAddedGroups_noGroupsAdded() {
    TpsPaoGetResult inputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs().addInputsItem(createGroupPolicyInput("group1")));
    HashSet<String> results = TpsUtilities.getAddedGroups(inputs, inputs);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetAddedGroups_groupAdded() {
    String group1Name = "group1";
    String group2Name = "group2";

    TpsPaoGetResult originalInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs().addInputsItem(createGroupPolicyInput(group1Name)));
    TpsPaoGetResult addedGroupInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs()
                    .addInputsItem(createGroupPolicyInput(group1Name))
                    .addInputsItem(createGroupPolicyInput(group2Name)));

    HashSet<String> results = TpsUtilities.getAddedGroups(originalInputs, addedGroupInputs);
    assertIterableEquals(List.of(group2Name), results);
  }

  @Test
  void testGetAddedGroups_groupRemoved() {
    String group1Name = "group1";
    String group2Name = "group2";

    TpsPaoGetResult originalInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs()
                    .addInputsItem(createGroupPolicyInput(group1Name))
                    .addInputsItem(createGroupPolicyInput(group2Name)));
    TpsPaoGetResult removedGroupInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs().addInputsItem(createGroupPolicyInput(group1Name)));

    HashSet<String> results = TpsUtilities.getAddedGroups(originalInputs, removedGroupInputs);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetRemovedGroups_noGroupPolicies() {
    TpsPaoGetResult inputs =
        new TpsPaoGetResult()
            .effectiveAttributes(new TpsPolicyInputs().addInputsItem(createProtectedPolicyInput()));
    HashSet<String> results = TpsUtilities.getRemovedGroups(inputs, inputs);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetRemovedGroups_noGroupsRemoved() {
    TpsPaoGetResult inputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs().addInputsItem(createGroupPolicyInput("group1")));
    HashSet<String> results = TpsUtilities.getRemovedGroups(inputs, inputs);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetRemovedGroups_groupAdded() {
    String group1Name = "group1";
    TpsPaoGetResult originalInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs().addInputsItem(createGroupPolicyInput(group1Name)));
    TpsPaoGetResult addedGroupInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs()
                    .addInputsItem(createGroupPolicyInput(group1Name))
                    .addInputsItem(createGroupPolicyInput("group2")));
    HashSet<String> results = TpsUtilities.getRemovedGroups(originalInputs, addedGroupInputs);
    assertIterableEquals(List.of(), results);
  }

  @Test
  void testGetRemovedGroups_groupRemoved() {
    String group1Name = "group1";
    String group2Name = "group2";

    TpsPaoGetResult originalInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs()
                    .addInputsItem(createGroupPolicyInput(group1Name))
                    .addInputsItem(createGroupPolicyInput(group2Name)));
    TpsPaoGetResult removedGroupInputs =
        new TpsPaoGetResult()
            .effectiveAttributes(
                new TpsPolicyInputs().addInputsItem(createGroupPolicyInput(group1Name)));

    HashSet<String> results = TpsUtilities.getRemovedGroups(originalInputs, removedGroupInputs);
    assertIterableEquals(List.of(group2Name), results);
  }

  @Test
  void testContainsProtectedDataPolicy() {
    assertTrue(
        TpsUtilities.containsProtectedDataPolicy(
            new TpsPolicyInputs().addInputsItem(createProtectedPolicyInput())));
  }

  @Test
  void testContainsProtectedDataPolicy_missing() {
    assertFalse(
        TpsUtilities.containsProtectedDataPolicy(
            new TpsPolicyInputs()
                .addInputsItem(
                    new TpsPolicyInput()
                        .namespace("other")
                        .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME))));
  }

  @Test
  void testContainsProtectedDataPolicy_null() {
    assertFalse(TpsUtilities.containsProtectedDataPolicy(null));
  }

  private TpsPolicyInput createGroupPolicyInput(String groupName) {
    return new TpsPolicyInput()
        .namespace(TpsUtilities.TERRA_NAMESPACE)
        .name(TpsUtilities.GROUP_CONSTRAINT)
        .addAdditionalDataItem(new TpsPolicyPair().key(TpsUtilities.GROUP_KEY).value(groupName));
  }

  private TpsPolicyInput createProtectedPolicyInput() {
    return new TpsPolicyInput()
        .namespace(TpsUtilities.TERRA_NAMESPACE)
        .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME);
  }
}
