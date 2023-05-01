package bio.terra.workspace.service.policy;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.workspace.common.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TpsUtilitiesTest extends BaseUnitTest {

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
                            new TpsPolicyPair().key(TpsUtilities.GROUP_KEY).value(testGroup))));

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
}
