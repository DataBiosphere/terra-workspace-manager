// package bio.terra.workspace.service.policy.model;
//
// import bio.terra.policy.model.TpsPaoConflict;
// import bio.terra.policy.model.TpsPaoGetResult;
// import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
// import bio.terra.workspace.service.policy.TpsApiConversionUtils;
// import java.util.List;
//
// public record PolicyUpdateResult(
// boolean updateApplied,
// TpsPaoGetResult resultingPolicy,
// List<TpsPaoConflict> conflicts
// ) {
// public ApiWsmPolicyUpdateResult toApi() {
//     var result = new ApiWsmPolicyUpdateResult();
//     result.updateApplied(updateApplied);
//     result.resultingPolicy(TpsApiConversionUtils.apiFromTpsPao(resultingPolicy));
//     result.conflicts(TpsApiConversionUtils.apiFromTpsPaoConflictList(conflicts));
//     return result;
// }
// }
