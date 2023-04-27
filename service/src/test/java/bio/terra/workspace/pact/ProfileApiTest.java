package bio.terra.workspace.pact;


import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import org.mockito.Mockito;

import java.util.Optional;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class ProfileApiTest {

  static final String billingProfileId = "4a5afeaa-b3b2-fa51-8e4e-9dbf294b7837";

  @Pact(consumer = "wsm-consumer", provider = "bpm-provider")
  public RequestResponsePact retrievingABillingProfile(PactDslWithProvider builder) {
    var cloudPlatformStrings =  "AZURE|GCP";
        //Arrays.stream(CloudPlatform.values()).map(Enum::name).collect(Collectors.joining("|"));
    var billingProfileResponseShape = new PactDslJsonBody()
        .stringMatcher("cloudPlatform", cloudPlatformStrings)
        .uuid("tenantId")
        .uuid("subscriptionId")
        .stringType("managedResourceGroupId")
        .stringType("billingAccountId")
        .uuid("id");
    return builder
        .uponReceiving("A request to retrieve a billing profile")
        .method("GET")
        .path(String.format("/api/profiles/v1/%s", billingProfileId))
        .willRespondWith().status(200)
        .body(billingProfileResponseShape)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "retrievingABillingProfile")
  public void testAuthorizingLinking(MockServer mockServer) throws Exception {
    var config = new SpendProfileConfiguration();

    config.setBasePath(mockServer.getUrl());

    var samService = Mockito.mock(SamService.class);

    var userRequest = new AuthenticatedUserRequest();
    userRequest.token(Optional.of("dummyValue"));
    var spendProfileId = new SpendProfileId(billingProfileId);
    var service = new SpendProfileService(samService, config);

    service.authorizeLinking(spendProfileId, true, userRequest);

  }


}
