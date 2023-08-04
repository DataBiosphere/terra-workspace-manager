package bio.terra.workspace.pact;


import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

import static au.com.dius.pact.consumer.dsl.DslPart.UUID_REGEX;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class TpsApiTest {

  // Note: the header must match exactly so pact doesn't add it's own
  // if "Content-type" is specified instead,
  // pact will also have a required header for "Content-Type: application/json; charset=UTF-8"
  // which will cause the request to fail to match,
  // since our client doesn't include the encoding in the content type header
  static Map<String, String> contentTypeJsonHeader = Map.of("Content-Type", "application/json");

  // TODO don't use a random value
  UUID objectId = UUID.randomUUID();
  UUID secondObjectId = UUID.randomUUID();

  static String existingWorkspacePolicyState = "an existing workspace policy";

  static PactDslJsonBody tpsPolicyInputsObjectShape = new PactDslJsonBody().object(
      "inputs",
      new PactDslJsonArray().eachArrayLike().object().stringType("namespace").stringType("name").closeObject()
  );

  static PactDslJsonBody workspacePolicyCreateRequestShape = new PactDslJsonBody()
      .uuid("objectId")
      .stringMatcher(
          "objectType",
          Arrays.stream(TpsObjectType.values()).map(TpsObjectType::getValue).collect(Collectors.joining("|"))
      )
      .stringMatcher(
          "component",
          Arrays.stream(TpsComponent.values()).map(TpsComponent::getValue).collect(Collectors.joining("|"))
      )
      .object("attributes", tpsPolicyInputsObjectShape);


  TpsApiDispatch dispatch;


  @BeforeEach
  void setup(MockServer mockServer) throws Exception {
    var tpsConfig = mock(PolicyServiceConfiguration.class);
    when(tpsConfig.getAccessToken()).thenReturn("dummyToken");
    when(tpsConfig.getBasePath()).thenReturn(mockServer.getUrl());
    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(true);
    dispatch = new TpsApiDispatch(featureConfig, tpsConfig);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact createPaoWithNoExistingPolicy(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to create a policy")
        .method("POST")
        .path("/api/policy/v1alpha1/pao")
        .body(workspacePolicyCreateRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "createPaoWithNoExistingPolicy")
  public void testCreatingAPolicyWithNoExistingPolicy() throws Exception {
    dispatch.createPao(
        UUID.randomUUID(),
        new TpsPolicyInputs().inputs(List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace"))),
        TpsComponent.WSM,
        TpsObjectType.WORKSPACE
    );
  }

  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact createPaoWithAPreexistingPolicy(PactDslWithProvider builder) {
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to create a policy")
        .method("POST")
        .path("/api/policy/v1alpha1/pao")
        .body(workspacePolicyCreateRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .status(HttpStatus.CONFLICT.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "createPaoWithAPreexistingPolicy")
  public void creatingAPolicyThatAlreadyExists() {
    assertThrows(
        PolicyConflictException.class,
        () -> dispatch.createPao(
            objectId,
            new TpsPolicyInputs().inputs(List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace"))),
            TpsComponent.WSM,
            TpsObjectType.WORKSPACE
        )
    );
  }

  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact deletePaoThatExists(PactDslWithProvider builder) {
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to delete a policy")
        .method("DELETE")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}",
            "/api/policy/v1alpha1/pao/%s".formatted(objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "deletePaoThatExists")
  public void deletingAnExistingPolicy() throws Exception {
    dispatch.deletePao(objectId);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact getPaoWithAnExistingWorkspacePolicy(PactDslWithProvider builder) {
    var policyResponseShape = new PactDslJsonBody()
        .valueFromProviderState("objectId", "${objectId}", objectId)
        .stringValue("component", TpsComponent.WSM.getValue())
        .stringValue("objectType", TpsObjectType.WORKSPACE.getValue())
        .object("effectiveAttributes", tpsPolicyInputsObjectShape)
        .object("attributes", tpsPolicyInputsObjectShape);

    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to retrieve a policy")
        .method("GET")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}",
            String.format("/api/policy/v1alpha1/pao/%s", objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(policyResponseShape)
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "getPaoWithAnExistingWorkspacePolicy")
  public void retrievingAnExistingWorkspacePolicy() throws Exception {
    var result = dispatch.getPao(objectId);
    assertNotNull(result);
    assertEquals(TpsComponent.WSM, result.getComponent());
    assertEquals(TpsObjectType.WORKSPACE, result.getObjectType());
    assertEquals(objectId, result.getObjectId());
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


  @Pact(consumer = "wsm", provider = "tps")
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
  @PactTestFor(pactMethod = "getPaoThatDoesNotExist")
  public void retrievingAPolicyThatDoesNotExist() {
    assertThrows(PolicyServiceNotFoundException.class, () -> dispatch.getPao(objectId));
  }

  /*
  TODO:
   test different update modes
          we use TpsUpdateMode.FAIL_ON_CONFLICT, and TpsUpdateMode.DRY_RUN
          ENFORCE_CONFLICT is not found in the project
   */

  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact linkPaoWhenBothExist(PactDslWithProvider builder) {
    var linkRequestShape = new PactDslJsonBody()
        .valueFromProviderState("sourceObjectId", "${secondObjectId}", secondObjectId)
        .stringMatcher("updateMode",
            Arrays.stream(TpsUpdateMode.values()).map(TpsUpdateMode::getValue).collect(Collectors.joining("|"))
        );
    var linkResponseShape = new PactDslJsonBody()
        .booleanType("updateApplied")
        .object("conflicts", new PactDslJsonArray())
        .object("resultingPao");

    return builder
        .given(existingWorkspacePolicyState)
        // TODO: better second policy state
        .given("another existing policy")
        .uponReceiving("A request to link the policies")
        .method("POST")
        .headers(contentTypeJsonHeader)
        .body(linkRequestShape)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}/link",
            String.format("/api/policy/v1alpha1/pao/%s/link", objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(linkResponseShape)
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "linkPaoWhenBothExist")
  public void linkTwoExistingPaosWithNoConflicts() throws Exception {
    var result = dispatch.linkPao(objectId, secondObjectId, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact mergePaoWhenBothExist(PactDslWithProvider builder) {
    var linkRequestShape = new PactDslJsonBody()
        .valueFromProviderState("sourceObjectId", "${secondObjectId}", secondObjectId)
        .stringMatcher("sourceObjectId", UUID_REGEX)
        .stringMatcher("updateMode",
            Arrays.stream(TpsUpdateMode.values()).map(TpsUpdateMode::getValue).collect(Collectors.joining("|"))
        );
    var linkResponseShape = new PactDslJsonBody()
        .booleanType("updateApplied")
        .object("conflicts", new PactDslJsonArray())
        .object("resultingPao");

    return builder
        .given(existingWorkspacePolicyState)
        .given("another existing policy")
        .uponReceiving("A request to link the policies")
        .method("POST")
        .headers(contentTypeJsonHeader)
        .body(linkRequestShape)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}/merge",
            String.format("/api/policy/v1alpha1/pao/%s/merge", objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(linkResponseShape)
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "mergePaoWhenBothExist")
  public void mergingTwoExistingPaos() throws Exception {
    var result = dispatch.mergePao(objectId, secondObjectId, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact replacePaoThatExists(PactDslWithProvider builder) {
    var updateRequestShape = new PactDslJsonBody()
        .object("newAttributes", tpsPolicyInputsObjectShape)
        .stringMatcher("updateMode",
            Arrays.stream(TpsUpdateMode.values()).map(TpsUpdateMode::getValue).collect(Collectors.joining("|"))
        );
    var updateResponseShape = new PactDslJsonBody()
        .booleanType("updateApplied")
        .object("conflicts", new PactDslJsonArray())
        .object("resultingPao"); // TpsPaoGetResult
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to update a policy")
        .method("PUT")
        .body(updateRequestShape)
        .headers(contentTypeJsonHeader)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}",
            "/api/policy/v1alpha1/pao/%s".formatted(objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(updateResponseShape)
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "replacePaoThatExists")
  public void replacingAnExistingPaoWithNoConflicts() throws Exception {
    var newInputs = new TpsPolicyInputs().inputs(List.of(
        new TpsPolicyInput().name("new_name").namespace("new_namespace")
    ));
    var result = dispatch.replacePao(objectId, newInputs, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact updatePaoPreexistingNoConflicts(PactDslWithProvider builder) {
    var updateRequestShape = new PactDslJsonBody()
        .object("addAttributes", tpsPolicyInputsObjectShape)
        .object("removeAttributes", tpsPolicyInputsObjectShape)
        .stringMatcher("updateMode",
            Arrays.stream(TpsUpdateMode.values()).map(TpsUpdateMode::getValue).collect(Collectors.joining("|"))
        );
    var updateResponseShape = new PactDslJsonBody()
        .booleanType("updateApplied")
        .object("conflicts", new PactDslJsonArray())
        .object("resultingPao"); // TpsPaoGetResult
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to update a policy")
        .method("PATCH")
        .body(updateRequestShape)
        .headers(contentTypeJsonHeader)
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}",
            "/api/policy/v1alpha1/pao/%s".formatted(objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(updateResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "updatePaoPreexistingNoConflicts")
  public void updatingAnExistingPaoWithNoConflicts() throws Exception {
    var remove = new TpsPolicyInputs().inputs(List.of(
        new TpsPolicyInput().name("test_name").namespace("test_namespace")
    ));
    var add = new TpsPolicyInputs().inputs(List.of(
        new TpsPolicyInput().name("new_name").namespace("new_namespace")
    ));
    var result = dispatch.updatePao(objectId, add, remove, TpsUpdateMode.FAIL_ON_CONFLICT);
    assertNotNull(result);
  }

  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact listValidRegions(PactDslWithProvider builder) {
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to list the valid regions for a policy")
        .method("GET")
        .query("platform=%s".formatted(CloudPlatform.AZURE.toTps()))
        .pathFromProviderState(
            "/api/policy/v1alpha1/region/${objectId}/list-valid",
            "/api/policy/v1alpha1/region/%s/list-valid".formatted(objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(new PactDslJsonArray().stringType())
        .toPact();
  }


  @Test
  @PactTestFor(pactMethod = "listValidRegions")
  public void listingValidRegionsOnAWorkspace() throws Exception {
    var result = dispatch.listValidRegions(objectId, CloudPlatform.AZURE);
    assertNotNull(result);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact listValidByPolicyInput(PactDslWithProvider builder) {
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to list the valid regions for a policy")
        .method("POST")
        .body(tpsPolicyInputsObjectShape)
        .query("platform=%s".formatted(CloudPlatform.AZURE.toTps()))
        .path("/api/policy/v1alpha1/location/list-valid")
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(new PactDslJsonArray().stringType())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "listValidByPolicyInput")
  public void listingValidRegionsOnAPolicy() throws Exception {
    var inputs = new TpsPolicyInputs()
        .inputs(List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace")));
    var tpsPao = new TpsPaoGetResult()
        .objectId(objectId)
        .attributes(inputs)
        .effectiveAttributes(inputs);
    var result = dispatch.listValidRegionsForPao(tpsPao, CloudPlatform.AZURE);
    assertNotNull(result);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact explainingAWorkspacePolicy(PactDslWithProvider builder) {
    var explainResponse = new PactDslJsonBody()
        .numberType("depth")
        .valueFromProviderState("objectId", "${objectId}", objectId.toString())
        .object(
            "policyInput",
            new PactDslJsonBody().stringType("namespace").stringType("name").closeObject()
        );
    return builder
        .given(existingWorkspacePolicyState)
        .uponReceiving("A request to explain the policy")
        .method("GET")
        .query("depth=1")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}/explain",
            String.format("/api/policy/v1alpha1/pao/%s/explain", objectId))
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(explainResponse)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "explainingAWorkspacePolicy")
  public void explainingAPolicy() throws Exception {
    var workspace = Workspace.builder()
        .workspaceId(objectId)
        .workspaceStage(WorkspaceStage.MC_WORKSPACE)
        .createdByEmail("")
        .build();
    var userRequest = mock(AuthenticatedUserRequest.class, RETURNS_SMART_NULLS);
    var workspaceService = mock(WorkspaceService.class);
    when(workspaceService.validateWorkspaceAndAction(any(), any(), any())).thenReturn(workspace);
    var result = dispatch.explain(objectId, 1, workspaceService, userRequest);
    assertNotNull(result);
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact getLocationInfo(PactDslWithProvider builder) {
    var locationArray = new PactDslJsonArray().eachArrayLike().object().stringType("name")
        .stringType("description")
        .object("regions", new PactDslJsonArray().stringType()).closeObject();
    var responseShape = new PactDslJsonBody()
        .stringType("name")
        .stringType("description")
        .object("regions", new PactDslJsonArray().stringType())
        .object("locations", locationArray);
    return builder
        .uponReceiving("A request for information about a location")
        .method("GET")
        // TODO: find a way to improve query string matching (this may require updating pact
        .query("platform=%s&location=%s".formatted(CloudPlatform.AZURE.toTps(), "europe"))
        .path("/api/policy/v1alpha1/location")
        .willRespondWith()
        .status(HttpStatus.OK.value())
        .body(responseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "getLocationInfo")
  public void retrievingInformationOnARegion() throws Exception {
    var result = dispatch.getLocationInfo(CloudPlatform.AZURE, "europe");
    assertNotNull(result);
  }


}
