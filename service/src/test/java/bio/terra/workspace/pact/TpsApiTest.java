package bio.terra.workspace.pact;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static bio.terra.workspace.pact.PactFixtures.CONTENT_TYPE_JSON_HEADER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.policy.model.*;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.PolicyServiceConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.exception.PolicyServiceNotFoundException;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import io.opentelemetry.api.OpenTelemetry;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class TpsApiTest {
  private static final String UUID_REGEX =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  // The policy ids aren't significant - hardcoded instead of random to avoid changing the pact on
  // every run
  static UUID existingPolicyId = UUID.fromString("bea1edd0-a8e6-4d60-a613-e8e065f21616");
  static String existingPolicyState = "an existing policy";
  static String existingPolicyProviderStateValue = "${policyId}";
  static UUID secondPolicyId = UUID.fromString("a254714b-4519-4ce4-ad87-19a7143376f4");
  static String secondPolicyState = "a second existing policy";
  static String secondPolicyProviderStateValue = "${secondPolicyId}";

  // A regex that matches any value of CloudPlatform that has a tps string
  static String cloudPlatformTpsRegex =
      Arrays.stream(CloudPlatform.values())
          .filter(p -> p != CloudPlatform.ANY) // ANY doesn not have a valid TPS string
          .map(CloudPlatform::toTps)
          .collect(Collectors.joining("|"));

  // A regex that matches any value of TpsUpdateMode
  static String updateModeRegex =
      Arrays.stream(TpsUpdateMode.values())
          .map(TpsUpdateMode::getValue)
          .collect(Collectors.joining("|"));

  // A regex that matches any value of TpsComponent
  static String tpsComponentRegex =
      Arrays.stream(TpsComponent.values())
          .map(TpsComponent::getValue)
          .collect(Collectors.joining("|"));

  // A regex that matches any value of TpsObjectType
  static String tpsObjectTypeRegex =
      Arrays.stream(TpsObjectType.values())
          .map(TpsObjectType::getValue)
          .collect(Collectors.joining("|"));

  static PactDslJsonBody tpsPolicyInputsObjectShape =
      new PactDslJsonBody()
          .object(
              "inputs",
              new PactDslJsonArray()
                  .eachArrayLike()
                  .object()
                  .stringType("namespace")
                  .stringType("name")
                  .closeObject());

  static PactDslJsonBody workspacePolicyCreateRequestShape =
      new PactDslJsonBody()
          .uuid("objectId")
          .stringMatcher("objectType", tpsObjectTypeRegex)
          .stringMatcher("component", tpsComponentRegex)
          .object("attributes", tpsPolicyInputsObjectShape);

  TpsApiDispatch dispatch;

  @BeforeEach
  void setup(MockServer mockServer) throws Exception {
    var tpsConfig = mock(PolicyServiceConfiguration.class);
    when(tpsConfig.getAccessToken()).thenReturn("dummyToken");
    when(tpsConfig.getBasePath()).thenReturn(mockServer.getUrl());
    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(true);
    dispatch = new TpsApiDispatch(featureConfig, tpsConfig, OpenTelemetry.noop());
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact createPaoWithNoExistingPolicy(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to create a policy")
        .method("POST")
        .path("/api/policy/v1alpha1/pao")
        .body(workspacePolicyCreateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "createPaoWithNoExistingPolicy", pactVersion = PactSpecVersion.V3)
  public void testCreatingAPolicyWithNoExistingPolicy() throws Exception {
    dispatch.createPao(
        UUID.randomUUID(),
        new TpsPolicyInputs()
            .inputs(List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace"))),
        TpsComponent.WSM,
        TpsObjectType.WORKSPACE);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact createPaoWithAPreexistingPolicy(PactDslWithProvider builder) {
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to create a policy that already exists")
        .method("POST")
        .path("/api/policy/v1alpha1/pao")
        .body(workspacePolicyCreateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .status(HttpStatus.CONFLICT.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "createPaoWithAPreexistingPolicy", pactVersion = PactSpecVersion.V3)
  public void creatingAPolicyThatAlreadyExists() {
    assertThrows(
        PolicyConflictException.class,
        () ->
            dispatch.createPao(
                existingPolicyId,
                new TpsPolicyInputs()
                    .inputs(
                        List.of(
                            new TpsPolicyInput().name("test_name").namespace("test_namespace"))),
                TpsComponent.WSM,
                TpsObjectType.WORKSPACE));
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact deletePaoThatExists(PactDslWithProvider builder) {
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to delete a policy")
        .method("DELETE")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "deletePaoThatExists", pactVersion = PactSpecVersion.V3)
  public void deletingAnExistingPolicy() throws Exception {
    dispatch.deletePao(existingPolicyId);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact getPaoWithAnExistingPolicy(PactDslWithProvider builder) {
    var policyResponseShape =
        new PactDslJsonBody()
            .valueFromProviderState(
                "objectId", existingPolicyProviderStateValue, existingPolicyId.toString())
            .stringMatcher("component", tpsComponentRegex)
            .stringMatcher("objectType", tpsObjectTypeRegex)
            .object("effectiveAttributes", tpsPolicyInputsObjectShape)
            .object("attributes", tpsPolicyInputsObjectShape);

    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to retrieve a policy")
        .method("GET")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(policyResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "getPaoWithAnExistingPolicy", pactVersion = PactSpecVersion.V3)
  public void retrievingAnExistingPolicy() throws Exception {
    var result = dispatch.getPao(existingPolicyId);
    assertNotNull(result);
    assertEquals(existingPolicyId, result.getObjectId());
    assertNotNull(result.getAttributes());
    assertNotNull(result.getAttributes().getInputs());
    var inputs = result.getAttributes().getInputs();
    assertTrue(inputs.size() > 0);
    var policy = inputs.get(0);
    assertNotNull(policy);
    assertNotNull(policy.getName());
    assertNotNull(policy.getNamespace());
    assertFalse(policy.getName().isEmpty());
    assertFalse(policy.getNamespace().isEmpty());
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact getPaoThatDoesNotExist(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to retrieve a policy that doesn't exist")
        .method("GET")
        .matchPath("/api/policy/v1alpha1/pao/%s".formatted(UUID_REGEX))
        .willRespondWith()
        .status(HttpStatus.NOT_FOUND.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "getPaoThatDoesNotExist", pactVersion = PactSpecVersion.V3)
  public void retrievingAPolicyThatDoesNotExist() {
    assertThrows(PolicyServiceNotFoundException.class, () -> dispatch.getPao(existingPolicyId));
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact linkPaoWhenBothExist(PactDslWithProvider builder) {
    var linkRequestShape =
        new PactDslJsonBody()
            .valueFromProviderState(
                "sourceObjectId", secondPolicyProviderStateValue, secondPolicyId)
            .stringMatcher("sourceObjectId", UUID_REGEX)
            .stringMatcher("updateMode", updateModeRegex);
    var linkResponseShape =
        new PactDslJsonBody()
            .booleanType("updateApplied")
            .object("conflicts", new PactDslJsonArray())
            .object("resultingPao");

    return builder
        .given(existingPolicyState)
        .given(secondPolicyState)
        .uponReceiving("A request to link the policies")
        .method("POST")
        .headers(CONTENT_TYPE_JSON_HEADER)
        .body(linkRequestShape)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s/link".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s/link".formatted(existingPolicyId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(linkResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "linkPaoWhenBothExist", pactVersion = PactSpecVersion.V3)
  public void linkTwoExistingPaosWithNoConflicts() throws Exception {
    var result = dispatch.linkPao(existingPolicyId, secondPolicyId, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact mergePaoWhenBothExist(PactDslWithProvider builder) {
    var linkRequestShape =
        new PactDslJsonBody()
            .valueFromProviderState(
                "sourceObjectId", secondPolicyProviderStateValue, secondPolicyId)
            .stringMatcher("sourceObjectId", UUID_REGEX)
            .stringMatcher("updateMode", updateModeRegex);
    var linkResponseShape =
        new PactDslJsonBody()
            .booleanType("updateApplied")
            .object("conflicts", new PactDslJsonArray())
            .object("resultingPao");

    return builder
        .given(existingPolicyState)
        .given(secondPolicyState)
        .uponReceiving("A request to link the policies")
        .method("POST")
        .headers(CONTENT_TYPE_JSON_HEADER)
        .body(linkRequestShape)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s/merge".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s/merge".formatted(existingPolicyId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(linkResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "mergePaoWhenBothExist", pactVersion = PactSpecVersion.V3)
  public void mergingTwoExistingPaos() throws Exception {
    var result =
        dispatch.mergePao(existingPolicyId, secondPolicyId, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact replacePaoThatExists(PactDslWithProvider builder) {
    var updateRequestShape =
        new PactDslJsonBody()
            .object("newAttributes", tpsPolicyInputsObjectShape)
            .stringMatcher("updateMode", updateModeRegex);
    var updateResponseShape =
        new PactDslJsonBody()
            .booleanType("updateApplied")
            .object("conflicts", new PactDslJsonArray())
            .object("resultingPao"); // TpsPaoGetResult
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to update a policy")
        .method("PUT")
        .body(updateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(updateResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "replacePaoThatExists", pactVersion = PactSpecVersion.V3)
  public void replacingAnExistingPaoWithNoConflicts() throws Exception {
    var newInputs =
        new TpsPolicyInputs()
            .inputs(List.of(new TpsPolicyInput().name("new_name").namespace("new_namespace")));
    var result = dispatch.replacePao(existingPolicyId, newInputs, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact updatePaoPreexistingNoConflicts(PactDslWithProvider builder) {
    var updateRequestShape =
        new PactDslJsonBody()
            .object("addAttributes", tpsPolicyInputsObjectShape)
            .object("removeAttributes", tpsPolicyInputsObjectShape)
            .stringMatcher("updateMode", updateModeRegex);
    var updateResponseShape =
        new PactDslJsonBody()
            .booleanType("updateApplied")
            .object("conflicts", new PactDslJsonArray())
            .object("resultingPao"); // TpsPaoGetResult
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to update a policy")
        .method("PATCH")
        .body(updateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s".formatted(existingPolicyId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(updateResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "updatePaoPreexistingNoConflicts", pactVersion = PactSpecVersion.V3)
  public void updatingAnExistingPaoWithNoConflicts() throws Exception {
    var remove =
        new TpsPolicyInputs()
            .inputs(List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace")));
    var add =
        new TpsPolicyInputs()
            .inputs(List.of(new TpsPolicyInput().name("new_name").namespace("new_namespace")));
    var result = dispatch.updatePao(existingPolicyId, add, remove, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact listValidRegions(PactDslWithProvider builder) {
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to list the valid regions for a policy using the id")
        .method("GET")
        .pathFromProviderState(
            "/api/policy/v1alpha1/region/%s/list-valid".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/region/%s/list-valid".formatted(existingPolicyId))
        .matchQuery("platform", cloudPlatformTpsRegex)
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(new PactDslJsonArray().stringType())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "listValidRegions", pactVersion = PactSpecVersion.V3)
  public void listingValidRegionsOnAWorkspace() throws Exception {
    var result = dispatch.listValidRegions(existingPolicyId, CloudPlatform.AZURE);
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact listValidByPolicyInput(PactDslWithProvider builder) {
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to list the valid regions for a policy using policy input")
        .method("POST")
        .body(tpsPolicyInputsObjectShape)
        .path("/api/policy/v1alpha1/location/list-valid")
        .matchQuery("platform", cloudPlatformTpsRegex)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(new PactDslJsonArray().stringType())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "listValidByPolicyInput", pactVersion = PactSpecVersion.V3)
  public void listingValidRegionsOnAPolicy() throws Exception {
    var inputs =
        new TpsPolicyInputs()
            .inputs(List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace")));
    var tpsPao =
        new TpsPaoGetResult()
            .objectId(existingPolicyId)
            .attributes(inputs)
            .effectiveAttributes(inputs);
    var result = dispatch.listValidRegionsForPao(tpsPao, CloudPlatform.AZURE);
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact explainingAWorkspacePolicy(PactDslWithProvider builder) {
    var explainResponse =
        new PactDslJsonBody()
            .numberType("depth")
            .valueFromProviderState(
                "objectId", existingPolicyProviderStateValue, existingPolicyId.toString())
            .object(
                "policyInput",
                newJsonBody(
                        input -> {
                          input.stringType("namespace");
                          input.stringType("name");
                        })
                    .build());
    return builder
        .given(existingPolicyState)
        .uponReceiving("A request to explain the policy")
        .method("GET")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/%s/explain".formatted(existingPolicyProviderStateValue),
            "/api/policy/v1alpha1/pao/%s/explain".formatted(existingPolicyId))
        .matchQuery("depth", "\\d+")
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(explainResponse)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "explainingAWorkspacePolicy", pactVersion = PactSpecVersion.V3)
  public void explainingAPolicy() throws Exception {
    var workspace =
        Workspace.builder()
            .workspaceId(existingPolicyId)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail("")
            .build();
    var userRequest = mock(AuthenticatedUserRequest.class, RETURNS_SMART_NULLS);
    var workspaceService = mock(WorkspaceService.class);
    when(workspaceService.validateWorkspaceAndAction(any(), any(), any())).thenReturn(workspace);
    var result = dispatch.explain(existingPolicyId, 1, workspaceService, userRequest);
    assertNotNull(result);
    assertNotNull(result.toApi());
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact getLocationInfo(PactDslWithProvider builder) {
    var locationArray =
        new PactDslJsonArray()
            .eachArrayLike()
            .object()
            .stringType("name")
            .stringType("description")
            .object("regions", new PactDslJsonArray().stringType())
            .closeObject();
    var responseShape =
        new PactDslJsonBody()
            .stringType("name")
            .stringType("description")
            .object("regions", new PactDslJsonArray().stringType())
            .object("locations", locationArray);
    return builder
        .uponReceiving("A request for information about a location")
        .method("GET")
        .path("/api/policy/v1alpha1/location")
        .matchQuery("platform", cloudPlatformTpsRegex)
        .matchQuery("location", ".+", List.of("usa", "europe", "iowa"))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(responseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "getLocationInfo", pactVersion = PactSpecVersion.V3)
  public void retrievingInformationOnALocation() throws Exception {
    var result = dispatch.getLocationInfo(CloudPlatform.AZURE, "europe");
    assertNotNull(result);
  }

  @Pact(consumer = "workspacemanager", provider = "tps")
  public RequestResponsePact getLocationInfoWithNullLocation(PactDslWithProvider builder) {
    var locationArray =
        new PactDslJsonArray()
            .eachArrayLike()
            .object()
            .stringType("name")
            .stringType("description")
            .object("regions", new PactDslJsonArray().stringType())
            .closeObject();
    var responseShape =
        new PactDslJsonBody()
            .stringType("name")
            .stringType("description")
            .object("regions", new PactDslJsonArray().stringType())
            .object("locations", locationArray);
    return builder
        .uponReceiving("A request for information about a null location")
        .method("GET")
        .path("/api/policy/v1alpha1/location")
        .matchQuery("platform", cloudPlatformTpsRegex)
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(responseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "getLocationInfoWithNullLocation", pactVersion = PactSpecVersion.V3)
  public void retrievingInformationOnANullLocation() throws Exception {
    // when location is null, it defaults to "global" in TPS
    var result = dispatch.getLocationInfo(CloudPlatform.AZURE, null);
    assertNotNull(result);
  }
}
