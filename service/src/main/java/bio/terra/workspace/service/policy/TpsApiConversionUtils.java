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
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoConflict;
import bio.terra.workspace.generated.model.ApiTpsPaoDescription;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import bio.terra.workspace.generated.model.ApiTpsUpdateMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The WSM interface uses an identical, but different set of classes for TPS data. This utility
 * class holds static methods to do extremely simple, if laborious conversions between the two
 * classes. It is our buffer when TPS API changes and WSM is not in sync.
 */
public class TpsApiConversionUtils {

  public static List<ApiTpsPolicyInput> apiEffectivePolicyListFromTpsPao(TpsPaoGetResult tpsPao) {
    ApiTpsPolicyInputs apiInputs = apiFromTpsPolicyInputs(tpsPao.getEffectiveAttributes());
    return apiInputs.getInputs();
  }

  private static ApiTpsPolicyInputs apiFromTpsPolicyInputs(TpsPolicyInputs policyInputs) {
    List<ApiTpsPolicyInput> policyList = new ArrayList<>();
    for (TpsPolicyInput tpsInput : policyInputs.getInputs()) {
      policyList.add(apiFromTpsPolicyInput(tpsInput));
    }

    return new ApiTpsPolicyInputs().inputs(policyList);
  }

  public static ApiTpsPolicyInput apiFromTpsPolicyInput(TpsPolicyInput tpsInput) {
    List<ApiTpsPolicyPair> apiAdditionalData = new ArrayList<>();
    for (TpsPolicyPair tpsPair : tpsInput.getAdditionalData()) {
      apiAdditionalData.add(new ApiTpsPolicyPair().key(tpsPair.getKey()).value(tpsPair.getValue()));
    }

    return new ApiTpsPolicyInput()
        .namespace(tpsInput.getNamespace())
        .name(tpsInput.getName())
        .additionalData(apiAdditionalData);
  }

  public static ApiTpsPaoUpdateResult apiFromTpsUpdateResult(TpsPaoUpdateResult tpsUpdateResult) {
    return new ApiTpsPaoUpdateResult()
        .updateApplied(tpsUpdateResult.isUpdateApplied())
        .resultingPao(apiFromTpsPao(tpsUpdateResult.getResultingPao()))
        .conflicts(apiFromTpsPaoConflictList(tpsUpdateResult.getConflicts()));
  }

  public static ApiTpsPaoGetResult apiFromTpsPao(TpsPaoGetResult tpsPao) {
    if (tpsPao == null) {
      return null;
    }
    return new ApiTpsPaoGetResult()
        .objectId(tpsPao.getObjectId())
        .component(apiFromTpsComponent(tpsPao.getComponent()))
        .objectType(apiFromTpsObjectType(tpsPao.getObjectType()))
        .attributes(apiFromTpsPolicyInputs(tpsPao.getAttributes()))
        .effectiveAttributes(apiFromTpsPolicyInputs(tpsPao.getEffectiveAttributes()))
        .deleted(tpsPao.isDeleted())
        .sourcesObjectIds(tpsPao.getSourcesObjectIds());
  }

  public static List<ApiTpsPaoConflict> apiFromTpsPaoConflictList(
      List<TpsPaoConflict> conflictList) {
    List<ApiTpsPaoConflict> apiConflicts = new ArrayList<>();
    for (TpsPaoConflict tpsConflict : conflictList) {
      apiConflicts.add(
          new ApiTpsPaoConflict()
              .namespace(tpsConflict.getNamespace())
              .name(tpsConflict.getName())
              .conflictPao(apiFromTpsPaoDescription(tpsConflict.getConflictPao()))
              .targetPao(apiFromTpsPaoDescription(tpsConflict.getTargetPao())));
    }
    return apiConflicts;
  }

  public static ApiTpsPaoDescription apiFromTpsPaoDescription(TpsPaoDescription tpsDescription) {
    return new ApiTpsPaoDescription()
        .objectId(tpsDescription.getObjectId())
        .component(apiFromTpsComponent(tpsDescription.getComponent()))
        .objectType(apiFromTpsObjectType(tpsDescription.getObjectType()));
  }

  public static ApiTpsComponent apiFromTpsComponent(TpsComponent tpsComponent) {
    return ApiTpsComponent.fromValue(tpsComponent.getValue());
  }

  public static ApiTpsObjectType apiFromTpsObjectType(TpsObjectType tpsObjectType) {
    return ApiTpsObjectType.fromValue(tpsObjectType.getValue());
  }

  public static TpsPolicyInput tpsFromApiPolicyInput(ApiTpsPolicyInput apiInput) {
    List<TpsPolicyPair> additionalData = new ArrayList<>();
    for (ApiTpsPolicyPair apiPair : apiInput.getAdditionalData()) {
      additionalData.add(new TpsPolicyPair().key(apiPair.getKey()).value(apiPair.getValue()));
    }

    return new TpsPolicyInput()
        .namespace(apiInput.getNamespace())
        .name(apiInput.getName())
        .additionalData(additionalData);
  }

  public static TpsPolicyInputs tpsFromApiTpsPolicyInputs(ApiTpsPolicyInputs apiInputs) {
    if (apiInputs == null) {
      return null;
    }
    return new TpsPolicyInputs()
        .inputs(
            apiInputs.getInputs().stream()
                .map(TpsApiConversionUtils::tpsFromApiPolicyInput)
                .toList());
  }

  public static TpsUpdateMode tpsFromApiTpsUpdateMode(ApiTpsUpdateMode apiUpdateMode) {
    TpsUpdateMode mode = TpsUpdateMode.fromValue(apiUpdateMode.name());
    if (mode == null) {
      throw new EnumNotRecognizedException("No mapping for update mode");
    }
    return mode;
  }
}
