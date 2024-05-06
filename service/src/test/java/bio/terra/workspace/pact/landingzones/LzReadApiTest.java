package bio.terra.workspace.pact.landingzones;

import static bio.terra.workspace.pact.PactFixtures.BILLING_PROFILE_ID;
import static bio.terra.workspace.pact.PactFixtures.LANDING_ZONE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.workspace.amalgam.landingzone.azure.HttpLandingZoneService;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceNotFoundException;
import bio.terra.workspace.app.configuration.external.LandingZoneServiceConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import io.opentelemetry.api.OpenTelemetry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

/**
 * Exercises LZS APIs concerned with reading landing zones (get landing zone, read resources, etc.)
 */
@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class LzReadApiTest {

  HttpLandingZoneService landingZoneService;

  static final UUID azureResourceId = UUID.fromString("e3327645-1e8f-454d-bdae-fe06b4762542");

  static final DslPart resourceShape =
      new PactDslJsonBody()
          .stringType("resourceId")
          .stringType("resourceType")
          .stringType("resourceName")
          .stringType("resourceParentId")
          .stringType("region")
          .object("tags");

  static final DslPart resourcesByTypeShape =
      new PactDslJsonBody()
          .stringType("description")
          .stringType("purpose", "SHARED_RESOURCE")
          .eachLike("deployedResources", resourceShape);

  @BeforeEach
  void setup(MockServer mockServer) {
    var config = mock(LandingZoneServiceConfiguration.class);
    when(config.getBasePath()).thenReturn(mockServer.getUrl());
    landingZoneService = new HttpLandingZoneService(OpenTelemetry.noop(), config);
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getLandingZone(PactDslWithProvider builder) {
    var getResultResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId")
            .uuid("billingProfileId")
            .stringType("definition")
            .stringType("version")
            .stringType("region")
            .date("createdDate");

    return builder
        .given("An existing landing zone")
        .uponReceiving("A request to get a landing zone")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${LANDING_ZONE_ID}",
            "/api/landingzones/v1/azure/%s".formatted(LANDING_ZONE_ID))
        .willRespondWith()
        .body(getResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getLandingZone_notFound(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request for a non-existent landing zone")
        .method("GET")
        .path(String.format("/api/landingzones/v1/azure/%s", LANDING_ZONE_ID))
        .willRespondWith()
        .status(404)
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact listLandingZonesForBillingProfile(PactDslWithProvider builder) {
    var listResponseShape =
        new PactDslJsonBody()
            .array("landingzones")
            .object()
            .uuid("landingZoneId")
            .uuid("billingProfileId")
            .stringType("definition")
            .stringType("version")
            .stringType("region")
            .date("createdDate");

    return builder
        .given("A landing zone linked to a billing profile")
        .uponReceiving("A request to list landing zones for a billing profile")
        .method("GET")
        .path("/api/landingzones/v1/azure")
        .query(String.format("billingProfileId=%s", BILLING_PROFILE_ID))
        .willRespondWith()
        .body(listResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact listLandingZones(PactDslWithProvider builder) {
    var listResponseShape =
        new PactDslJsonBody()
            .eachLike("landingzones")
            .uuid("landingZoneId")
            .uuid("billingProfileId")
            .stringType("definition")
            .stringType("version")
            .stringType("region")
            .date("createdDate");

    return builder
        .uponReceiving("A request to list all landing zones")
        .method("GET")
        .path("/api/landingzones/v1/azure")
        .willRespondWith()
        .body(listResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact listLandingZoneDefinitions(PactDslWithProvider builder) {
    var definitionsResponseShape =
        new PactDslJsonBody()
            .eachLike("landingzones")
            .stringType("definition")
            .stringType("name")
            .stringType("description")
            .stringType("version");

    return builder
        .uponReceiving("A request to list landing zone defs")
        .method("GET")
        .path("/api/landingzones/definitions/v1/azure")
        .willRespondWith()
        .body(definitionsResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact listAzureLandingZoneResources(PactDslWithProvider builder) {
    var resourcesResponseShape =
        new PactDslJsonBody().uuid("id").eachLike("resources", resourcesByTypeShape);

    return builder
        .given("An existing landing zone")
        .uponReceiving("A request to list the landing zone resources")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${LANDING_ZONE_ID}/resources",
            "/api/landingzones/v1/azure/%s/resources".formatted(LANDING_ZONE_ID))
        .willRespondWith()
        .body(resourcesResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getResourceQuota(PactDslWithProvider builder) {
    var quotaResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId")
            .stringType("azureResourceId")
            .stringType("resourceType")
            .object("quotaValues");

    return builder
        .given("An existing landing zone")
        .uponReceiving("A request to get a resource's quota")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${LANDING_ZONE_ID}/resource-quota",
            "/api/landingzones/v1/azure/%s/resource-quota".formatted(LANDING_ZONE_ID))
        .query(String.format("azureResourceId=%s", azureResourceId))
        .willRespondWith()
        .body(quotaResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "listLandingZonesForBillingProfile", pactVersion = PactSpecVersion.V3)
  void listLandingZonesForBillingProfile() throws InterruptedException {
    var result =
        landingZoneService.listLandingZonesByBillingProfile(
            new BearerToken("fake"), BILLING_PROFILE_ID);

    assertNotNull(result);
    assertThat(result.getLandingzones(), is(not(empty())));
  }

  @Test
  @PactTestFor(pactMethod = "listLandingZones", pactVersion = PactSpecVersion.V3)
  void listLandingZones() throws InterruptedException {
    var result = landingZoneService.listLandingZonesByBillingProfile(new BearerToken("fake"), null);

    assertNotNull(result);
    assertThat(result.getLandingzones(), is(not(empty())));
    assertNotNull(result.getLandingzones().get(0).getLandingZoneId());
    assertNotNull(result.getLandingzones().get(0).getBillingProfileId());
    assertNotNull(result.getLandingzones().get(0).getDefinition());
    assertNotNull(result.getLandingzones().get(0).getRegion());
    assertNotNull(result.getLandingzones().get(0).getVersion());
    assertNotNull(result.getLandingzones().get(0).getCreatedDate());
  }

  @Test
  @PactTestFor(pactMethod = "getLandingZone", pactVersion = PactSpecVersion.V3)
  void getLandingZoneRegion() throws InterruptedException {
    var result = landingZoneService.getLandingZoneRegion(new BearerToken("fake"), LANDING_ZONE_ID);

    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "getLandingZone", pactVersion = PactSpecVersion.V3)
  void getAzureLandingZone() throws InterruptedException {
    var result = landingZoneService.getAzureLandingZone(new BearerToken("fake"), LANDING_ZONE_ID);

    assertNotNull(result);
    assertNotNull(result.getCreatedDate());
    assertNotNull(result.getBillingProfileId());
    assertNotNull(result.getRegion());
    assertNotNull(result.getVersion());
    assertNotNull(result.getLandingZoneId());
    assertNotNull(result.getDefinition());
  }

  @Test
  @PactTestFor(pactMethod = "getLandingZone_notFound", pactVersion = PactSpecVersion.V3)
  void landingZoneNotFound() {
    assertThrows(
        LandingZoneServiceNotFoundException.class,
        () -> landingZoneService.getAzureLandingZone(new BearerToken("fake"), LANDING_ZONE_ID));
  }

  @Test
  @PactTestFor(pactMethod = "listLandingZoneDefinitions", pactVersion = PactSpecVersion.V3)
  void listLandingZoneDefinitions() throws InterruptedException {
    var result = landingZoneService.listLandingZoneDefinitions(new BearerToken("fake"));

    assertNotNull(result);
    assertThat(result.getLandingzones(), is(not(empty())));
  }

  @Test
  @PactTestFor(pactMethod = "listAzureLandingZoneResources", pactVersion = PactSpecVersion.V3)
  void listLandingZoneResources() throws InterruptedException {
    var result =
        landingZoneService.listResourcesWithPurposes(new BearerToken("fake"), LANDING_ZONE_ID);

    assertNotNull(result);
    assertThat(result.getResources(), is(not(empty())));
    assertThat(result.getResources().get(0).getDeployedResources(), is(not(empty())));
  }

  @Test
  @PactTestFor(pactMethod = "listAzureLandingZoneResources", pactVersion = PactSpecVersion.V3)
  void listLandingZoneResourcesMatchingPurpose() throws InterruptedException {
    var result =
        landingZoneService.listResourcesMatchingPurpose(
            new BearerToken("fake"), LANDING_ZONE_ID, ResourcePurpose.SHARED_RESOURCE);

    assertNotNull(result);
    assertThat(result.getResources(), is(not(empty())));
    var purposes =
        result.getResources().stream().map(ApiAzureLandingZoneResourcesPurposeGroup::getPurpose);
    assertThat(
        purposes.allMatch(r -> r.equals(ResourcePurpose.SHARED_RESOURCE.getValue())),
        equalTo(true));
  }

  @Test
  @PactTestFor(pactMethod = "getResourceQuota", pactVersion = PactSpecVersion.V3)
  void getResourceQuota() {
    var result =
        landingZoneService.getResourceQuota(
            new BearerToken("fake"), LANDING_ZONE_ID, azureResourceId.toString());

    assertNotNull(result);
    assertNotNull(result.getAzureResourceId());
    assertNotNull(result.getResourceType());
    assertNotNull(result.getLandingZoneId());
    assertNotNull(result.getQuotaValues());
    assertThat(result.getQuotaValues(), is(anEmptyMap()));
  }
}
