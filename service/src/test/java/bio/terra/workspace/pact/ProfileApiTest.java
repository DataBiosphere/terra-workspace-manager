package bio.terra.workspace.pact;

import static org.junit.jupiter.api.Assertions.assertThrows;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class ProfileApiTest {

  // This will need to be set up with BPM
  static final String billingProfileId = "4a5afeaa-b3b2-fa51-8e4e-9dbf294b7837";

  @Pact(consumer = "wsm-consumer", provider = "bpm-provider")
  public RequestResponsePact billingProfileAvailable(PactDslWithProvider builder) {
    // Using the WSM version of this, instead of the BPM version,
    // because what we care about is not what BPM sends, but that WSM can handle the value
    var cloudPlatformStrings =
        Stream.of(CloudPlatform.AZURE, CloudPlatform.GCP)
            .map(CloudPlatform::toSql)
            .collect(Collectors.joining("|"));
    var billingProfileResponseShape =
        new PactDslJsonBody()
            .stringMatcher("cloudPlatform", cloudPlatformStrings)
            .uuid("tenantId")
            .uuid("subscriptionId")
            .stringType("managedResourceGroupId")
            .stringType("billingAccountId")
            .uuid("id");
    return builder
        .given("an existing billing profile")
        .uponReceiving("A request to retrieve a billing profile")
        .method("GET")
        .pathFromProviderState(
            "/api/profiles/v1/${profileId}", String.format("/api/profiles/v1/%s", billingProfileId))
        .willRespondWith()
        .status(200)
        .body(billingProfileResponseShape)
        .toPact();
  }

  @Pact(consumer = "wsm-consumer", provider = "bpm-provider")
  public RequestResponsePact billingProfileUnAvailable(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to retrieve a billing profile")
        .method("GET")
        .pathFromProviderState(
            "/api/profiles/v1/${profileId}", String.format("/api/profiles/v1/%s", billingProfileId))
        .willRespondWith()
        .status(403)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "billingProfileAvailable")
  public void testAuthorizingLinkingOfAnExistingProfile(MockServer mockServer) {
    var config = new SpendProfileConfiguration();

    config.setBasePath(mockServer.getUrl());

    var samService = Mockito.mock(SamService.class);

    var userRequest = new AuthenticatedUserRequest();
    userRequest.token(Optional.of("dummyValue"));
    var spendProfileId = new SpendProfileId(billingProfileId);
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
    var spendProfileId = new SpendProfileId(billingProfileId);
    var service = new SpendProfileService(samService, config);
    assertThrows(
        SpendUnauthorizedException.class,
        () -> service.authorizeLinking(spendProfileId, true, userRequest));
  }
}
