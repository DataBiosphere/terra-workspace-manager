package bio.terra.workspace.service.policy;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.generated.model.ApiWsmPolicy;
import bio.terra.workspace.generated.model.ApiWsmPolicyComponent;
import bio.terra.workspace.generated.model.ApiWsmPolicyConflict;
import bio.terra.workspace.generated.model.ApiWsmPolicyDescription;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyObjectType;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateMode;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import java.util.ArrayList;
import java.util.List;

/**
 * The WSM interface uses an identical, but different set of classes for TPS data. This utility
 * class holds static methods to do extremely simple, if laborious conversions between the two
 * classes. It is our buffer when TPS API changes and WSM is not in sync.
 */
public class TpsApiConversionUtils {

  public static List<ApiWsmPolicyInput> apiEffectivePolicyListFromTpsPao(TpsPaoGetResult tpsPao) {
    ApiWsmPolicyInputs apiInputs = apiFromTpsPolicyInputs(tpsPao.getEffectiveAttributes());
    return apiInputs.getInputs();
  }

  private static ApiWsmPolicyInputs apiFromTpsPolicyInputs(TpsPolicyInputs policyInputs) {
    List<ApiWsmPolicyInput> policyList = new ArrayList<>();
    for (TpsPolicyInput tpsInput : policyInputs.getInputs()) {
      policyList.add(apiFromTpsPolicyInput(tpsInput));
    }

    return new ApiWsmPolicyInputs().inputs(policyList);
  }

  public static ApiWsmPolicyInput apiFromTpsPolicyInput(TpsPolicyInput tpsInput) {
    List<ApiWsmPolicyPair> apiAdditionalData = new ArrayList<>();
    for (TpsPolicyPair tpsPair : tpsInput.getAdditionalData()) {
      apiAdditionalData.add(new ApiWsmPolicyPair().key(tpsPair.getKey()).value(tpsPair.getValue()));
    }

    return new ApiWsmPolicyInput()
        .namespace(tpsInput.getNamespace())
        .name(tpsInput.getName())
        .additionalData(apiAdditionalData);
  }

  public static ApiWsmPolicyUpdateResult apiFromTpsUpdateResult(
      TpsPaoUpdateResult tpsUpdateResult) {
    return new ApiWsmPolicyUpdateResult()
        .updateApplied(tpsUpdateResult.isUpdateApplied())
        .resultingPolicy(apiFromTpsPao(tpsUpdateResult.getResultingPao()))
        .conflicts(apiFromTpsPaoConflictList(tpsUpdateResult.getConflicts()));
  }

  public static ApiWsmPolicy apiFromTpsPao(TpsPaoGetResult tpsPao) {
    if (tpsPao == null) {
      return null;
    }
    return new ApiWsmPolicy()
        .objectId(tpsPao.getObjectId())
        .component(apiFromTpsComponent(tpsPao.getComponent()))
        .objectType(apiFromTpsObjectType(tpsPao.getObjectType()))
        .attributes(apiFromTpsPolicyInputs(tpsPao.getAttributes()))
        .effectiveAttributes(apiFromTpsPolicyInputs(tpsPao.getEffectiveAttributes()))
        .deleted(tpsPao.isDeleted())
        .sourcesObjectIds(tpsPao.getSourcesObjectIds());
  }

  public static List<ApiWsmPolicyConflict> apiFromTpsPaoConflictList(
      List<TpsPaoConflict> conflictList) {
    List<ApiWsmPolicyConflict> apiConflicts = new ArrayList<>();
    for (TpsPaoConflict tpsConflict : conflictList) {
      apiConflicts.add(
          new ApiWsmPolicyConflict()
              .namespace(tpsConflict.getNamespace())
              .name(tpsConflict.getName())
              .conflictPolicy(apiFromTpsPaoDescription(tpsConflict.getConflictPao()))
              .targetPolicy(apiFromTpsPaoDescription(tpsConflict.getTargetPao())));
    }
    return apiConflicts;
  }

  public static ApiWsmPolicyDescription apiFromTpsPaoDescription(TpsPaoDescription tpsDescription) {
    return new ApiWsmPolicyDescription()
        .objectId(tpsDescription.getObjectId())
        .component(apiFromTpsComponent(tpsDescription.getComponent()))
        .objectType(apiFromTpsObjectType(tpsDescription.getObjectType()));
  }

  public static ApiWsmPolicyComponent apiFromTpsComponent(TpsComponent tpsComponent) {
    return ApiWsmPolicyComponent.fromValue(tpsComponent.getValue());
  }

  public static ApiWsmPolicyObjectType apiFromTpsObjectType(TpsObjectType tpsObjectType) {
    return ApiWsmPolicyObjectType.fromValue(tpsObjectType.getValue());
  }

  public static TpsPolicyInput tpsFromApiPolicyInput(ApiWsmPolicyInput apiInput) {
    List<TpsPolicyPair> additionalData = new ArrayList<>();
    for (ApiWsmPolicyPair apiPair : apiInput.getAdditionalData()) {
      additionalData.add(new TpsPolicyPair().key(apiPair.getKey()).value(apiPair.getValue()));
    }

    return new TpsPolicyInput()
        .namespace(apiInput.getNamespace())
        .name(apiInput.getName())
        .additionalData(additionalData);
  }

  public static TpsPolicyInputs tpsFromApiTpsPolicyInputs(ApiWsmPolicyInputs apiInputs) {
    if (apiInputs == null) {
      return null;
    }
    return new TpsPolicyInputs()
        .inputs(
            apiInputs.getInputs().stream()
                .map(TpsApiConversionUtils::tpsFromApiPolicyInput)
                .toList());
  }

  public static TpsUpdateMode tpsFromApiTpsUpdateMode(ApiWsmPolicyUpdateMode apiUpdateMode) {
    TpsUpdateMode mode = TpsUpdateMode.fromValue(apiUpdateMode.name());
    if (mode == null) {
      throw new EnumNotRecognizedException("No mapping for update mode");
    }
    return mode;
  }
}
