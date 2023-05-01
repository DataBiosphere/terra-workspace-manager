package bio.terra.workspace.service.policy;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyExplainSource;
import bio.terra.policy.model.TpsPolicyExplanation;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.generated.model.ApiWsmPolicy;
import bio.terra.workspace.generated.model.ApiWsmPolicyComponent;
import bio.terra.workspace.generated.model.ApiWsmPolicyConflict;
import bio.terra.workspace.generated.model.ApiWsmPolicyDescription;
import bio.terra.workspace.generated.model.ApiWsmPolicyExplanation;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyObjectType;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateMode;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.policy.model.PolicyComponent;
import bio.terra.workspace.service.policy.model.PolicyObject;
import bio.terra.workspace.service.policy.model.PolicyObjectType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The WSM interface uses an identical, but different set of classes for TPS data. This utility
 * class holds static methods to do extremely simple, if laborious conversions between the two
 * classes. It is our buffer when TPS API changes and WSM is not in sync.
 */
public class TpsApiConversionUtils {
  private static final Logger logger = LoggerFactory.getLogger(TpsApiConversionUtils.class);

  private TpsApiConversionUtils() {}

  public static List<ApiWsmPolicyInput> apiEffectivePolicyListFromTpsPao(TpsPaoGetResult tpsPao) {
    if (tpsPao == null) return new ArrayList<>();

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

  public static @Nullable ApiWsmPolicy apiFromTpsPao(@Nullable TpsPaoGetResult tpsPao) {
    if (tpsPao == null) {
      return null;
    }
    return new ApiWsmPolicy()
        .objectId(tpsPao.getObjectId())
        .component(tpsFromApiComponent(tpsPao.getComponent()))
        .objectType(tpsFromApiObjectType(tpsPao.getObjectType()))
        .attributes(apiFromTpsPolicyInputs(tpsPao.getAttributes()))
        .effectiveAttributes(apiFromTpsPolicyInputs(tpsPao.getEffectiveAttributes()))
        .deleted(tpsPao.isDeleted())
        .sourcesObjectIds(tpsPao.getSourcesObjectIds())
        .createdDate(tpsPao.getCreatedDate())
        .lastUpdatedDate(tpsPao.getLastUpdatedDate());
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
        .component(tpsFromApiComponent(tpsDescription.getComponent()))
        .objectType(tpsFromApiObjectType(tpsDescription.getObjectType()));
  }

  public static ApiWsmPolicyComponent tpsFromApiComponent(TpsComponent tpsComponent) {
    return ApiWsmPolicyComponent.fromValue(tpsComponent.getValue());
  }

  public static ApiWsmPolicyObjectType tpsFromApiObjectType(TpsObjectType tpsObjectType) {
    return ApiWsmPolicyObjectType.fromValue(tpsObjectType.getValue());
  }

  public static TpsPaoDescription tpsFromApiPaoDescription(
      ApiWsmPolicyDescription apiWsmPolicyDescription) {
    return new TpsPaoDescription()
        .objectId(apiWsmPolicyDescription.getObjectId())
        .component(tpsFromApiComponent(apiWsmPolicyDescription.getComponent()))
        .objectType(tpsFromApiObjectType(apiWsmPolicyDescription.getObjectType()));
  }

  public static TpsComponent tpsFromApiComponent(ApiWsmPolicyComponent apiWsmPolicyComponent) {
    return TpsComponent.fromValue(apiWsmPolicyComponent.toString());
  }

  public static TpsObjectType tpsFromApiObjectType(ApiWsmPolicyObjectType apiWsmPolicyObjectType) {
    return TpsObjectType.fromValue(apiWsmPolicyObjectType.toString());
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

  public static @Nullable TpsPolicyInputs tpsFromApiTpsPolicyInputs(
      @Nullable ApiWsmPolicyInputs apiInputs) {
    if (apiInputs == null) {
      return null;
    }
    return new TpsPolicyInputs()
        .inputs(
            apiInputs.getInputs().stream()
                .map(TpsApiConversionUtils::tpsFromApiPolicyInput)
                .toList());
  }

  public static ApiWsmPolicyInput policyInputToApi(TpsPolicyInput input) {
    var wsmInput = new ApiWsmPolicyInput();
    if (input.getAdditionalData() != null) {
      input
          .getAdditionalData()
          .forEach(
              data ->
                  wsmInput.addAdditionalDataItem(
                      new ApiWsmPolicyPair().key(data.getKey()).value(data.getValue())));
    }
    return wsmInput.namespace(input.getNamespace()).name(input.getName());
  }

  public static TpsUpdateMode tpsFromApiTpsUpdateMode(ApiWsmPolicyUpdateMode apiUpdateMode) {
    TpsUpdateMode mode = TpsUpdateMode.fromValue(apiUpdateMode.name());
    if (mode == null) {
      throw new EnumNotRecognizedException("No mapping for update mode");
    }
    return mode;
  }

  public static PolicyObject buildWsmPolicyObject(
      TpsPolicyExplainSource source,
      WorkspaceService workspaceService,
      AuthenticatedUserRequest userRequest) {
    boolean access = false;
    String name = null;
    Map<String, String> properties = Collections.emptyMap();
    // When there are more type of policy object, we may need to change this to a switch case.
    Preconditions.checkState(TpsObjectType.WORKSPACE == source.getObjectType());
    try {
      var workspace =
          workspaceService.validateWorkspaceAndAction(
              userRequest, source.getObjectId(), SamWorkspaceAction.READ);
      access = true;
      name = workspace.displayName();
      properties = workspace.properties();
    } catch (ForbiddenException e) {
      logger.info("Not authorized to read workspace {}.", source.getObjectId());
    }

    return new PolicyObject(
        source.getObjectId(),
        PolicyObjectType.fromTpsObjectType(source.getObjectType()),
        PolicyComponent.fromTpsComponent(source.getComponent()),
        source.isDeleted(),
        access,
        name,
        properties,
        source.getCreatedDate(),
        source.getLastUpdatedDate());
  }

  public static ApiWsmPolicyExplanation convertExplanation(TpsPolicyExplanation explanation) {
    var wsmPolicyExplanation =
        new ApiWsmPolicyExplanation()
            .objectId(explanation.getObjectId())
            .policyInput(policyInputToApi(explanation.getPolicyInput()));
    if (explanation.getPolicyExplanations() != null) {
      wsmPolicyExplanation.policyExplanations(
          explanation.getPolicyExplanations().stream()
              .map(TpsApiConversionUtils::convertExplanation)
              .toList());
    }
    return wsmPolicyExplanation;
  }
}
