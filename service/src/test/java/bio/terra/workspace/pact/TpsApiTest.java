package bio.terra.workspace.pact;


import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.PolicyServiceConfiguration;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class TpsApiTest {


  UUID objectId = UUID.randomUUID();

  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact retrievingAnExistingWorkspacePolicy(PactDslWithProvider builder) {
    var inputsShape = new PactDslJsonBody().object(
        "inputs",
        new PactDslJsonArray().eachArrayLike().object().stringType("namespace").stringType("name").closeObject()
    );
    var policyResponseShape = new PactDslJsonBody()
        .valueFromProviderState("objectId", "${objectId}", objectId)
        .stringValue("component", TpsComponent.WSM.getValue())
        .stringValue("objectType", TpsObjectType.WORKSPACE.getValue())
        .object("attributes", inputsShape);

    return builder
        .given("an existing policy for a workspace")
        .uponReceiving("A request to create a policy")
        .method("GET")
        .pathFromProviderState(
            "/api/policy/v1alpha1/pao/${objectId}",
            String.format("/api/policy/v1alpha1/pao/%s", objectId))
        .willRespondWith()
        .status(200)
        .body(policyResponseShape)
        .toPact();
  }


  @Pact(consumer = "wsm", provider = "tps")
  public RequestResponsePact creatingWithNoExistingPolicy(PactDslWithProvider builder) {
    var inputShape = new PactDslJsonBody().object(
        "inputs",
        new PactDslJsonArray().eachArrayLike().object().stringType("namespace").stringType("name").closeObject()
    );

    var requestShape = new PactDslJsonBody()
        .uuid("objectId")
        .stringMatcher(
            "objectType",
            Arrays.stream(TpsObjectType.values()).map(TpsObjectType::getValue).collect(Collectors.joining("|"))
        )
        .stringMatcher(
            "component",
            Arrays.stream(TpsComponent.values()).map(TpsComponent::getValue).collect(Collectors.joining("|"))
        )
        .object("attributes", inputShape);

    var pact = builder
        .uponReceiving("A request to create a policy")
        .method("POST")
        .path("/api/policy/v1alpha1/pao")
        .body(requestShape)
        // Note: the header must match exactly so pact doesn't add it's own
        // if "Content-type" is specified instead,
        // pact will also have a required header for "Content-Type: application/json; charset=UTF-8"
        // which will cause the request to fail to match,
        // since our client doesn't include the encoding in the content type header
        .headers(Map.of("Content-Type","application/json"))
        .willRespondWith()
        .status(200)
        .toPact();
    return pact;

  }

  @Test
  @PactTestFor(pactMethod = "creatingWithNoExistingPolicy")
  public void testCreatingAPolicyWithNoExistingPolicy(MockServer mockServer) throws Exception {
    var tpsConfig = mock(PolicyServiceConfiguration.class);
    when(tpsConfig.getAccessToken()).thenReturn("dummyToken");
    when(tpsConfig.getBasePath()).thenReturn(mockServer.getUrl());
    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(true);
    var dispatch = new TpsApiDispatch(featureConfig, tpsConfig);
    var inputs = List.of(new TpsPolicyInput().name("test_name").namespace("test_namespace"));
    dispatch.createPao(
        UUID.randomUUID(),
        new TpsPolicyInputs().inputs(inputs),
        TpsComponent.WSM,
        TpsObjectType.WORKSPACE
    );
  }


  @Test
  @PactTestFor(pactMethod = "retrievingAnExistingWorkspacePolicy")
  public void retrievingAnExistingWorkspacePolicy(MockServer mockServer) throws Exception {
    var tpsConfig = mock(PolicyServiceConfiguration.class);
    when(tpsConfig.getAccessToken()).thenReturn("dummyToken");
    when(tpsConfig.getBasePath()).thenReturn(mockServer.getUrl());
    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(true);
    var dispatch = new TpsApiDispatch(featureConfig, tpsConfig);
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

}
