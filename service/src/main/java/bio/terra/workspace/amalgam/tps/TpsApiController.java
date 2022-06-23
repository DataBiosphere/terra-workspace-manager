package bio.terra.workspace.amalgam.tps;

import bio.terra.policy.common.model.PolicyInput;
import bio.terra.policy.common.model.PolicyInputs;
import bio.terra.policy.service.pao.PaoService;
import bio.terra.policy.service.pao.model.Pao;
import bio.terra.policy.service.pao.model.PaoComponent;
import bio.terra.policy.service.pao.model.PaoObjectType;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.controller.TpsApi;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import bio.terra.workspace.generated.model.ApiTpsRegionConstraintRequest;
import bio.terra.workspace.generated.model.ApiTpsRegionConstraintResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class TpsApiController implements TpsApi {

  private final FeatureConfiguration features;
  private final HttpServletRequest request;
  private final PaoService paoService;

  @Autowired
  public TpsApiController(
      FeatureConfiguration features, HttpServletRequest request, PaoService paoService) {
    this.features = features;
    this.request = request;
    this.paoService = paoService;
  }

  // -- Policy Queries --
  @Override
  public ResponseEntity<ApiTpsRegionConstraintResult> policyRegionConstraint(
      ApiTpsRegionConstraintRequest body) {
    features.tpsEnabledCheck();
    return TpsApi.super.policyRegionConstraint(body);
    /*
     TODO: implementation - deferred until after group membership constraint
    */
  }

  // -- Policy Attribute Objects --
  @Override
  public ResponseEntity<Void> createPao(ApiTpsPaoCreateRequest body) {
    features.tpsEnabledCheck();

    // TODO: permissions

    paoService.createPao(
        body.getObjectId(),
        componentFromApi(body.getComponent()),
        objectTypeFromApi(body.getObjectType()),
        policyInputsFromApi(body.getAttributes()));

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deletePao(UUID objectId) {
    features.tpsEnabledCheck();
    paoService.deletePao(objectId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiTpsPaoGetResult> getPao(UUID objectId) {
    features.tpsEnabledCheck();
    // TODO: permissions
    Pao pao = paoService.getPao(objectId);
    return new ResponseEntity<>(paoToApi(pao), HttpStatus.OK);
  }

  // -- Api to Tps conversion methods --
  // Note: we need to keep the Api types out of the TPS library code. It does not build the Api so
  // we cannot
  // implement toApi/fromApi pattern in our internal TPS classes. Instead, we code them here.

  private PaoComponent componentFromApi(ApiTpsComponent apiComponent) {
    if (apiComponent == ApiTpsComponent.WSM) {
      return PaoComponent.WSM;
    }
    throw new EnumNotRecognizedException("invalid TpsComponent");
  }

  private ApiTpsComponent componentToApi(PaoComponent component) {
    if (component == PaoComponent.WSM) {
      return ApiTpsComponent.WSM;
    }
    throw new InternalLogicException("Invalid component");
  }

  private PaoObjectType objectTypeFromApi(ApiTpsObjectType apiObjectType) {
    if (apiObjectType == ApiTpsObjectType.WORKSPACE) {
      return PaoObjectType.WORKSPACE;
    }
    throw new EnumNotRecognizedException("invalid TpsObjectType");
  }

  private ApiTpsObjectType objectTypeToApi(PaoObjectType objectType) {
    if (objectType == PaoObjectType.WORKSPACE) {
      return ApiTpsObjectType.WORKSPACE;
    }
    throw new InternalLogicException("Invalid object type");
  }

  private PolicyInput policyInputFromApi(ApiTpsPolicyInput apiInput) {
    // These nulls shouldn't happen.
    if (apiInput == null || apiInput.getNamespace() == null || apiInput.getName() == null) {
      throw new InvalidInputException("PolicyInput namespace and name cannot be null");
    }

    Map<String, String> data;
    if (apiInput.getAdditionalData() == null) {
      // Ensure we always have a map, even if it is empty.
      data = new HashMap<>();
    } else {
      data =
          apiInput.getAdditionalData().stream()
              .collect(Collectors.toMap(ApiTpsPolicyPair::getKey, ApiTpsPolicyPair::getValue));
    }
    return new PolicyInput(apiInput.getNamespace(), apiInput.getName(), data);
  }

  public ApiTpsPolicyInput policyInputToApi(PolicyInput input) {
    List<ApiTpsPolicyPair> apiPolicyPairs =
        input.getAdditionalData().entrySet().stream()
            .map(e -> new ApiTpsPolicyPair().key(e.getKey()).value(e.getValue()))
            .toList();

    return new ApiTpsPolicyInput()
        .namespace(input.getNamespace())
        .name(input.getName())
        .additionalData(apiPolicyPairs);
  }

  private PolicyInputs policyInputsFromApi(@Nullable ApiTpsPolicyInputs apiInputs) {
    if (apiInputs == null || apiInputs.getInputs() == null || apiInputs.getInputs().isEmpty()) {
      return new PolicyInputs(new HashMap<>());
    }

    var inputs = new HashMap<String, PolicyInput>();
    for (ApiTpsPolicyInput apiInput : apiInputs.getInputs()) {
      // Convert the input so we get any errors before we process it further
      var input = policyInputFromApi(apiInput);
      String key = PolicyInputs.composeKey(input.getNamespace(), input.getName());
      if (inputs.containsKey(key)) {
        throw new InvalidInputException("Duplicate policy attribute in policy input: " + key);
      }
      inputs.put(key, input);
    }
    return new PolicyInputs(inputs);
  }

  public ApiTpsPolicyInputs policyInputsToApi(PolicyInputs inputs) {
    return new ApiTpsPolicyInputs()
        .inputs(inputs.getInputs().values().stream().map(this::policyInputToApi).toList());
  }

  public ApiTpsPaoGetResult paoToApi(Pao pao) {
    return new ApiTpsPaoGetResult()
        .objectId(pao.getObjectId())
        .component(componentToApi(pao.getComponent()))
        .objectType(objectTypeToApi(pao.getObjectType()))
        .attributes(policyInputsToApi(pao.getAttributes()))
        .effectiveAttributes(policyInputsToApi(pao.getEffectiveAttributes()))
        .inConflict(pao.isInConflict())
        .children(pao.getChildObjectIds());
  }
}
