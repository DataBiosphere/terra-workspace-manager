package bio.terra.workspace.service.policy;

import bio.terra.common.logging.RequestIdFilter;
import bio.terra.policy.api.TpsApi;
import bio.terra.policy.client.ApiClient;
import bio.terra.policy.client.ApiException;
import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoCreateRequest;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoReplaceRequest;
import bio.terra.policy.model.TpsPaoSourceRequest;
import bio.terra.policy.model.TpsPaoUpdateRequest;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.PolicyServiceConfiguration;
import bio.terra.workspace.service.policy.exception.PolicyServiceAPIException;
import bio.terra.workspace.service.policy.exception.PolicyServiceAuthorizationException;
import bio.terra.workspace.service.policy.exception.PolicyServiceDuplicateException;
import bio.terra.workspace.service.policy.exception.PolicyServiceNotFoundException;
import io.opencensus.contrib.http.jaxrs.JaxrsClientExtractor;
import io.opencensus.contrib.http.jaxrs.JaxrsClientFilter;
import io.opencensus.contrib.spring.aop.Traced;
import io.opencensus.trace.Tracing;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.client.Client;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TpsApiDispatch {
  // TODO: PF-1714 pass through bearer token in all cases

  private final FeatureConfiguration features;
  private final PolicyServiceConfiguration policyServiceConfiguration;
  private final Client commonHttpClient;

  @Autowired
  public TpsApiDispatch(
      FeatureConfiguration features, PolicyServiceConfiguration policyServiceConfiguration) {
    this.features = features;
    this.policyServiceConfiguration = policyServiceConfiguration;
    this.commonHttpClient =
        new ApiClient()
            .getHttpClient()
            .register(
                new JaxrsClientFilter(
                    new JaxrsClientExtractor(), Tracing.getPropagationComponent().getB3Format()));
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client =
        new ApiClient()
            .setHttpClient(commonHttpClient)
            .addDefaultHeader(
                RequestIdFilter.REQUEST_ID_HEADER, MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));
    client.setAccessToken(accessToken);
    return client;
  }

  private TpsApi policyApi() {
    try {
      return new TpsApi(
          getApiClient(policyServiceConfiguration.getAccessToken())
              .setBasePath(policyServiceConfiguration.getBasePath()));
    } catch (IOException e) {
      throw new PolicyServiceAuthorizationException(
          String.format(
              "Error reading or parsing credentials file at %s",
              policyServiceConfiguration.getClientCredentialFilePath()),
          e.getCause());
    }
  }

  // -- Api to Tps conversion methods --
  // Note: we need to keep the Api types out of the TPS library code. It does not build the Api so
  // we cannot implement toApi/fromApi pattern in our internal TPS classes. Instead, we code
  // them here.
  /*
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

   private static ApiTpsPolicyInput policyInputToApi(PolicyInput input) {
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

   private static PolicyInputs policyInputsFromApi(@Nullable ApiTpsPolicyInputs apiInputs) {
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

   private static ApiTpsPolicyInputs policyInputsToApi(PolicyInputs inputs) {

     // old policies could have been created with a null list.
     if (inputs == null) {
       return new ApiTpsPolicyInputs();
     }

     return new ApiTpsPolicyInputs()
         .inputs(
             inputs.getInputs().values().stream().map(TpsApiDispatch::policyInputToApi).toList());
   }

   private static ApiTpsPaoGetResult paoToApi(Pao pao) {
     return new ApiTpsPaoGetResult()
         .objectId(pao.getObjectId())
         .component(componentToApi(pao.getComponent()))
         .objectType(objectTypeToApi(pao.getObjectType()))
         .attributes(policyInputsToApi(pao.getAttributes()))
         .effectiveAttributes(policyInputsToApi(pao.getEffectiveAttributes()))
         .sourcesObjectIds(pao.getSourceObjectIds().stream().toList())
         .deleted((pao.getDeleted()));
   }

   private static PaoUpdateMode updateModeFromApi(ApiTpsUpdateMode apiUpdateMode) {
     switch (apiUpdateMode) {
       case DRY_RUN -> {
         return PaoUpdateMode.DRY_RUN;
       }
       case FAIL_ON_CONFLICT -> {
         return PaoUpdateMode.FAIL_ON_CONFLICT;
       }
       case ENFORCE_CONFLICT -> {
         return PaoUpdateMode.ENFORCE_CONFLICTS;
       }
     }
     throw new TpsInvalidInputException("Invalid update mode: " + apiUpdateMode);
   }

   private static ApiTpsPaoUpdateResult updateResultToApi(PolicyUpdateResult result) {
     ApiTpsPaoUpdateResult apiResult =
         new ApiTpsPaoUpdateResult()
             .updateApplied(result.updateApplied())
             .resultingPao(paoToApi(result.computedPao()));

     for (PolicyConflict conflict : result.conflicts()) {
       var apiConflict =
           new ApiTpsPaoConflict()
               .namespace(conflict.policyName().getNamespace())
               .name(conflict.policyName().getName())
               .targetPao(paoToApiPaoDescription(conflict.pao()))
               .conflictPao(paoToApiPaoDescription(conflict.conflictPao()));
       apiResult.addConflictsItem(apiConflict);
     }

     return apiResult;
   }

   private static ApiTpsPaoDescription paoToApiPaoDescription(Pao pao) {
     return new ApiTpsPaoDescription()
         .objectId(pao.getObjectId())
         .component(componentToApi(pao.getComponent()))
         .objectType(objectTypeToApi(pao.getObjectType()));
   }


  */

  private RuntimeException convertApiException(ApiException ex) {
    if (ex.getCode() == HttpStatus.UNAUTHORIZED.value()) {
      return new PolicyServiceAuthorizationException(
          "Not authorized to access Terra Policy Service", ex.getCause());
    } else if (ex.getCode() == HttpStatus.NOT_FOUND.value()) {
      return new PolicyServiceNotFoundException("Policy service not found", ex);
    } else if (ex.getCode() == HttpStatus.BAD_REQUEST.value()
        && StringUtils.containsIgnoreCase(ex.getMessage(), "duplicate")) {
      return new PolicyServiceDuplicateException("Policy service duplicate", ex);
    } else {
      return new PolicyServiceAPIException(ex);
    }
  }

  // -- Policy Attribute Object Interface --
  public void createEmptyPao(UUID workspaceId) {
    createPao(workspaceId, new TpsPolicyInputs());
  }

  public void createPao(UUID workspaceUuid, TpsPolicyInputs policyInputs) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();
    try {
      tpsApi.createPao(
          new TpsPaoCreateRequest()
              .objectId(workspaceUuid)
              .component(TpsComponent.WSM)
              .objectType(TpsObjectType.WORKSPACE)
              .attributes(policyInputs));
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  public void deletePao(UUID workspaceUuid) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();
    try {
      try {
        tpsApi.deletePao(workspaceUuid);
      } catch (ApiException e) {
        throw convertApiException(e);
      }
    } catch (PolicyServiceNotFoundException e) {
      // Not found is not an error as far as WSM is concerned.
    }
  }

  @Traced
  public Optional<TpsPaoGetResult> getPaoIfExists(UUID workspaceUuid) {
    features.tpsEnabledCheck();
    try {
      TpsPaoGetResult pao = getPao(workspaceUuid);
      return Optional.of(pao);
    } catch (PolicyServiceNotFoundException e) {
      return Optional.empty();
    }
  }

  public TpsPaoGetResult getPao(UUID workspaceUuid) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();
    try {
      return tpsApi.getPao(workspaceUuid);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  public TpsPaoUpdateResult linkPao(
      UUID workspaceUuid, UUID sourceObjectId, TpsUpdateMode updateMode) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();

    try {
      return tpsApi.linkPao(
          new TpsPaoSourceRequest().sourceObjectId(sourceObjectId).updateMode(updateMode),
          workspaceUuid);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  public TpsPaoUpdateResult mergePao(
      UUID workspaceUuid, UUID sourceObjectId, TpsUpdateMode updateMode) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();

    try {
      return tpsApi.mergePao(
          new TpsPaoSourceRequest().sourceObjectId(sourceObjectId).updateMode(updateMode),
          workspaceUuid);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  public TpsPaoUpdateResult replacePao(
      UUID workspaceUuid, TpsPolicyInputs policyInputs, TpsUpdateMode updateMode) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();

    try {
      return tpsApi.replacePao(
          new TpsPaoReplaceRequest().newAttributes(policyInputs).updateMode(updateMode),
          workspaceUuid);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  public TpsPaoUpdateResult updatePao(
      UUID workspaceUuid,
      TpsPolicyInputs addAttributes,
      TpsPolicyInputs removeAttributes,
      TpsUpdateMode updateMode) {
    features.tpsEnabledCheck();
    TpsApi tpsApi = policyApi();
    try {
      return tpsApi.updatePao(
          new TpsPaoUpdateRequest()
              .addAttributes(addAttributes)
              .removeAttributes(removeAttributes)
              .updateMode(updateMode),
          workspaceUuid);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }
}
