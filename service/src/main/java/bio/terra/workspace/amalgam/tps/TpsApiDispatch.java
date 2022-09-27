package bio.terra.workspace.amalgam.tps;

import bio.terra.common.iam.BearerToken;
import bio.terra.policy.common.exception.PolicyObjectNotFoundException;
import bio.terra.policy.common.model.PolicyInput;
import bio.terra.policy.common.model.PolicyInputs;
import bio.terra.policy.common.model.PolicyName;
import bio.terra.policy.service.pao.PaoService;
import bio.terra.policy.service.pao.model.Pao;
import bio.terra.policy.service.pao.model.PaoComponent;
import bio.terra.policy.service.pao.model.PaoObjectType;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TpsApiDispatch {
  // TODO: PF-1714 pass through bearer token in all cases

  private final FeatureConfiguration features;
  private final PaoService paoService;

  @Autowired
  public TpsApiDispatch(FeatureConfiguration features, PaoService paoService) {
    this.features = features;
    this.paoService = paoService;
  }

  // -- Policy Attribute Object Interface --
  public void createPao(BearerToken bearerToken, ApiTpsPaoCreateRequest request) {
    features.tpsEnabledCheck();
    paoService.createPao(
        request.getObjectId(),
        componentFromApi(request.getComponent()),
        objectTypeFromApi(request.getObjectType()),
        policyInputsFromApi(request.getAttributes()));
  }

  public void deletePao(BearerToken bearerToken, UUID objectId) {
    features.tpsEnabledCheck();
    try {
      paoService.deletePao(objectId);
    } catch (PolicyObjectNotFoundException e) {
      // Not found is not an error as far as WSM is concerned.
    }
  }

  public Optional<ApiTpsPaoGetResult> getPaoIfExists(BearerToken bearerToken, UUID objectId) {
    features.tpsEnabledCheck();
    try {
      Pao pao = paoService.getPao(objectId);
      return Optional.of(paoToApi(pao));
    } catch (PolicyObjectNotFoundException e) {
      return Optional.empty();
    }
  }

  public ApiTpsPaoGetResult getPao(BearerToken bearerToken, UUID objectId) {
    return getPaoIfExists(bearerToken, objectId)
        .orElseThrow(
            () -> new PolicyObjectNotFoundException("Policy object not found: " + objectId));
  }

  // -- Api to Tps conversion methods --
  // Note: we need to keep the Api types out of the TPS library code. It does not build the Api so
  // we cannot
  // implement toApi/fromApi pattern in our internal TPS classes. Instead, we code them here.

  private static PaoComponent componentFromApi(ApiTpsComponent apiComponent) {
    if (apiComponent == ApiTpsComponent.WSM) {
      return PaoComponent.WSM;
    }
    throw new EnumNotRecognizedException("Invalid TpsComponent");
  }

  private static ApiTpsComponent componentToApi(PaoComponent component) {
    if (component == PaoComponent.WSM) {
      return ApiTpsComponent.WSM;
    }
    throw new InternalLogicException("Invalid PaoComponent");
  }

  private static PaoObjectType objectTypeFromApi(ApiTpsObjectType apiObjectType) {
    if (apiObjectType == ApiTpsObjectType.WORKSPACE) {
      return PaoObjectType.WORKSPACE;
    }
    throw new EnumNotRecognizedException("invalid TpsObjectType");
  }

  private static ApiTpsObjectType objectTypeToApi(PaoObjectType objectType) {
    if (objectType == PaoObjectType.WORKSPACE) {
      return ApiTpsObjectType.WORKSPACE;
    }
    throw new InternalLogicException("Invalid PaoObjectType");
  }

  private static PolicyInput policyInputFromApi(ApiTpsPolicyInput apiInput) {
    // These nulls shouldn't happen.
    if (apiInput == null
        || StringUtils.isEmpty(apiInput.getNamespace())
        || StringUtils.isEmpty(apiInput.getName())) {
      throw new TpsInvalidInputException("PolicyInput namespace and name cannot be null");
    }

    Multimap<String, String> data = ArrayListMultimap.create();
    if (apiInput.getAdditionalData() != null) {
      apiInput.getAdditionalData().forEach(item -> data.put(item.getKey(), item.getValue()));
    }

    return new PolicyInput(new PolicyName(apiInput.getNamespace(), apiInput.getName()), data);
  }

  public static ApiTpsPolicyInput policyInputToApi(PolicyInput input) {
    List<ApiTpsPolicyPair> apiPolicyPairs =
        input.getAdditionalData().entries().stream()
            .map(e -> new ApiTpsPolicyPair().key(e.getKey()).value(e.getValue()))
            .toList();

    final PolicyName policyName = input.getPolicyName();
    return new ApiTpsPolicyInput()
        .namespace(policyName.getNamespace())
        .name(policyName.getName())
        .additionalData(apiPolicyPairs);
  }

  public static PolicyInputs policyInputsFromApi(@Nullable ApiTpsPolicyInputs apiInputs) {
    if (apiInputs == null || apiInputs.getInputs() == null || apiInputs.getInputs().isEmpty()) {
      return new PolicyInputs(new HashMap<>());
    }

    var inputs = new HashMap<String, PolicyInput>();
    for (ApiTpsPolicyInput apiInput : apiInputs.getInputs()) {
      // Convert the input so we get any errors before we process it further
      var input = policyInputFromApi(apiInput);
      String key = input.getKey();
      if (inputs.containsKey(key)) {
        throw new TpsInvalidInputException("Duplicate policy attribute in policy input: " + key);
      }
      inputs.put(key, input);
    }
    return new PolicyInputs(inputs);
  }

  public static ApiTpsPolicyInputs policyInputsToApi(PolicyInputs inputs) {

    // old policies could have been created with a null list.
    if (inputs == null) {
      return new ApiTpsPolicyInputs();
    }

    return new ApiTpsPolicyInputs()
        .inputs(
            inputs.getInputs().values().stream().map(TpsApiDispatch::policyInputToApi).toList());
  }

  public static ApiTpsPaoGetResult paoToApi(Pao pao) {
    return new ApiTpsPaoGetResult()
        .objectId(pao.getObjectId())
        .component(componentToApi(pao.getComponent()))
        .objectType(objectTypeToApi(pao.getObjectType()))
        .attributes(policyInputsToApi(pao.getAttributes()))
        .effectiveAttributes(policyInputsToApi(pao.getEffectiveAttributes()))
        .sourcesObjectIds(pao.getSourceObjectIds().stream().toList())
        .deleted((pao.getDeleted()));
  }

  public static Pao paoFromApi(@Nullable ApiTpsPaoGetResult api) {
    if (api == null) {
      return null;
    }
    return new Pao(
        api.getObjectId(),
        componentFromApi(api.getComponent()),
        objectTypeFromApi(api.getObjectType()),
        policyInputsFromApi(api.getAttributes()),
        policyInputsFromApi(api.getEffectiveAttributes()),
        new HashSet<>(api.getSourcesObjectIds()),
        api.isDeleted());
  }
}
