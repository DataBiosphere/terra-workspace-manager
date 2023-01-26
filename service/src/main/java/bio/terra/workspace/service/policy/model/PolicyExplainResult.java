package bio.terra.workspace.service.policy.model;

import bio.terra.policy.model.TpsPolicyExplanation;
import bio.terra.workspace.generated.model.ApiWsmPolicyExplainResult;
import bio.terra.workspace.service.policy.TpsApiConversionUtils;
import java.util.List;
import java.util.UUID;

public record PolicyExplainResult(
    UUID objectId,
    int depth,
    List<PolicyObject> explainObjects,
    List<TpsPolicyExplanation> explanations) {

  public ApiWsmPolicyExplainResult toApi() {
    var result = new ApiWsmPolicyExplainResult();
    result.depth(depth).objectId(objectId);
    result.explainObjects(explainObjects.stream().map(PolicyObject::toApi).toList());

    result.explanation(
        explanations.stream().map(TpsApiConversionUtils::convertExplanation).toList());

    return result;
  }
}
