package bio.terra.workspace.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.policy.model.TpsPolicyExplanation;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.generated.model.ApiWsmPolicyExplanation;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TpsApiConversionUtilsTest extends BaseSpringBootUnitTest {

  @Test
  public void convertExplanation() {
    var tpsPolicyExplanation = createExplanation(/* curr= */ 0, /* depth= */ 3);

    ApiWsmPolicyExplanation wsmPolicyExplanation =
        TpsApiConversionUtils.convertExplanation(tpsPolicyExplanation);

    assertPolicyExplainResultEqual(tpsPolicyExplanation, wsmPolicyExplanation);
  }

  private TpsPolicyExplanation createExplanation(int curr, int depth) {
    TpsPolicyExplanation explanation = new TpsPolicyExplanation();
    explanation
        .objectId(UUID.randomUUID())
        .policyInput(
            new TpsPolicyInput()
                .name("name")
                .namespace("namespace")
                .additionalData(
                    List.of(
                        new TpsPolicyPair().key("foo").value("bar"),
                        new TpsPolicyPair().key("foo1").value("bar1"))));
    if (curr == depth) {
      return explanation;
    }
    return explanation.policyExplanations(List.of(createExplanation(++curr, depth)));
  }

  private void assertPolicyExplainResultEqual(
      TpsPolicyExplanation expected, ApiWsmPolicyExplanation actual) {
    assertEquals(expected.getObjectId(), actual.getObjectId());
    assertPolicyInput(expected.getPolicyInput(), actual.getPolicyInput());
    if (expected.getPolicyExplanations() != null) {
      assertEquals(expected.getPolicyExplanations().size(), actual.getPolicyExplanations().size());
      assertPolicyExplainResultEqual(
          expected.getPolicyExplanations().get(0), actual.getPolicyExplanations().get(0));
    } else {
      assertNull(actual.getPolicyExplanations());
    }
  }

  private void assertPolicyInput(TpsPolicyInput expected, ApiWsmPolicyInput actual) {
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getNamespace(), actual.getNamespace());
    assertEquals(expected.getAdditionalData().size(), actual.getAdditionalData().size());
  }
}
