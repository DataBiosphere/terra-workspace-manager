package bio.terra.workspace.pact;

import static org.junit.jupiter.api.Assertions.assertThrows;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class ProfileApiTest {

  // The actual values of these are not significant.
  // They will be replaced by provider-side values when the pact is verified
  static final String dummyAzureProfileId = "4a5afeaa-b3b2-fa51-8e4e-9dbf294b7837";
  static final String dummyGCPProfileId = "37bfb7e0-8261-4160-9ae7-882800d6464f";

  @Pact(consumer = "wsm-consumer", provider = "bpm-provider")
  public V4Pact existingAzureBillingProfile(PactBuilder builder) {
    var billingProfileResponseShape =
        new PactDslJsonBody()
            .stringValue("cloudPlatform", CloudPlatform.AZURE.toSql())
            .uuid("tenantId")
            .uuid("subscriptionId")
            .stringType("managedResourceGroupId")
            .uuid("id");
    return builder
        .usingLegacyDsl()
        .given("an Azure billing profile")
        .uponReceiving("A request to retrieve a billing profile")
        .method("GET")
        // azureProfileId will be replaced with the value specified in the BPM provider tests when
        // the contract is
        // run against it
        .pathFromProviderState(
            "/api/profiles/v1/${azureProfileId}",
            String.format("/api/profiles/v1/%s", dummyAzureProfileId))
        .willRespondWith()
        .status(200)
        .body(billingProfileResponseShape)
        .toPact(V4Pact.class);
  }

  @Pact(consumer = "wsm-consumer", provider = "bpm-provider")
  public V4Pact existingGCPBillingProfile(PactBuilder builder) {
    var billingProfileResponseShape =
        new PactDslJsonBody()
            .stringValue("cloudPlatform", CloudPlatform.GCP.toSql())
            .stringType("billingAccountId")
            .uuid("id");
    return builder
        .usingLegacyDsl()
        .given("a GCP billing profile")
        .uponReceiving("A request to retrieve a billing profile")
        .method("GET")
        .pathFromProviderState(
            "/api/profiles/v1/${gcpProfileId}",
            String.format("/api/profiles/v1/%s", dummyGCPProfileId))
        .willRespondWith()
        .status(200)
        .body(billingProfileResponseShape)
        .toPact(V4Pact.class);
  }

  @Pact(consumer = "wsm-consumer", provider = "bpm-provider")
  public V4Pact billingProfileUnAvailable(PactBuilder builder) {
    return builder
        .usingLegacyDsl()
        .uponReceiving("A request to retrieve a billing profile")
        .method("GET")
        // there's no state set on this pact, so this id won't exist in bpm
        .path(String.format("/api/profiles/v1/%s", dummyAzureProfileId))
        .willRespondWith()
        .status(403)
        .toPact(V4Pact.class);
  }

  @Test
  @PactTestFor(pactMethod = "existingAzureBillingProfile")
  public void testAuthorizingLinkingOfAnAzureProfile(MockServer mockServer) {
    var config = new SpendProfileConfiguration();

    config.setBasePath(mockServer.getUrl());

    var samService = Mockito.mock(SamService.class);

    var userRequest = new AuthenticatedUserRequest();
    userRequest.token(Optional.of("dummyValue"));
    var spendProfileId = new SpendProfileId(dummyAzureProfileId);
    var service = new SpendProfileService(samService, config);

    service.authorizeLinking(spendProfileId, true, userRequest);
  }

  @Test
  @PactTestFor(pactMethod = "existingGCPBillingProfile")
  public void testAuthorizingLinkingOfGCPProfile(MockServer mockServer) {
    var config = new SpendProfileConfiguration();

    config.setBasePath(mockServer.getUrl());

    var samService = Mockito.mock(SamService.class);

    var userRequest = new AuthenticatedUserRequest();
    userRequest.token(Optional.of("dummyValue"));
    var spendProfileId = new SpendProfileId(dummyGCPProfileId);
    var service = new SpendProfileService(samService, config);

    service.authorizeLinking(spendProfileId, true, userRequest);
  }

  @Test
  @PactTestFor(pactMethod = "billingProfileUnAvailable")
  public void testAuthorizingLinkingOfAnNonexistantProfile(MockServer mockServer) {
    var config = new SpendProfileConfiguration();

    config.setBasePath(mockServer.getUrl());

    var samService = Mockito.mock(SamService.class);

    var userRequest = new AuthenticatedUserRequest();
    userRequest.token(Optional.of("dummyValue"));
    var spendProfileId = new SpendProfileId(dummyAzureProfileId);
    var service = new SpendProfileService(samService, config);
    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(spendProfileId, true, userRequest));
  }
}
