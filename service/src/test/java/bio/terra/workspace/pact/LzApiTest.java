package bio.terra.workspace.pact;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import bio.terra.common.iam.BearerToken;
import bio.terra.workspace.amalgam.landingzone.azure.HttpLandingZoneService;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceAuthorizationException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceNotFoundException;
import bio.terra.workspace.app.configuration.external.LandingZoneServiceConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class LzApiTest {

  HttpLandingZoneService landingZoneService;

  static final Map<String, String> contentTypeJsonHeader =
      Map.of("Content-Type", "application/json");
  static final UUID landingZoneId = UUID.fromString("a051c5f8-9de8-4b4f-a453-8f9645df9c7b");
  static final UUID asyncJobId = UUID.fromString("3b0db775-1916-42d2-aaeb-ebc7aea69eca");
  static final UUID billingProfileId = UUID.fromString("4955757a-b027-4b6c-b77b-4cba899864cd");

  static PactDslJsonBody landingZoneCreateRequestShape =
      new PactDslJsonBody()
          .stringType("landingZoneId")
          .stringType("definition")
          .stringType("version")
          .uuid("billingProfileId")
          .object("jobControl")
          .stringType("id")
          .closeObject()
          .minArrayLike(
              "parameters", 1, new PactDslJsonBody().stringType("key").stringType("value"));

  @BeforeEach
  void setup(MockServer mockServer) {
    var config = mock(LandingZoneServiceConfiguration.class);
    when(config.getBasePath()).thenReturn(mockServer.getUrl());
    landingZoneService = new HttpLandingZoneService(OpenTelemetry.noop(), config);
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startCreateLandingZone(PactDslWithProvider builder) {
    var createResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId")
            .object("jobReport")
            .stringType("id")
            .stringType("status", "RUNNING")
            .closeObject();

    return builder
        .uponReceiving("A request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .body(createResponseShape)
        .status(HttpStatus.ACCEPTED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact createLandingZone_notAuthorized(PactDslWithProvider builder) {
    return builder
        .uponReceiving("An unauthorized request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact createLandingZoneResult(PactDslWithProvider builder) {
    var createResultResponseShape =
        new PactDslJsonBody()
            .object("jobReport")
            .stringType("id")
            .stringType("status", "RUNNING")
            .closeObject();

    return builder
        .given("An existing landing zone creation job")
        .uponReceiving("A request to get the landing zone creation job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/create-result/${deleteJobId}",
            "/api/landingzones/v1/azure/create-result/%s".formatted(asyncJobId))
        .willRespondWith()
        .body(createResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startDeleteLandingZone(PactDslWithProvider builder) {
    var deleteRequestShape =
        new PactDslJsonBody().object("jobControl").stringType("id").closeObject();

    var deleteResponseShape =
        new PactDslJsonBody()
            .object("jobReport")
            .stringType("id")
            .stringType("status", "RUNNING")
            .closeObject();

    return builder
        .given("An existing landing zone")
        .uponReceiving("A request to delete a landing zone")
        .method("POST")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}",
            "/api/landingzones/v1/azure/%s".formatted(landingZoneId))
        .body(deleteRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .body(deleteResponseShape)
        .status(HttpStatus.ACCEPTED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact deleteLandingZoneResult(PactDslWithProvider builder) {
    var deleteResultResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId", landingZoneId)
            .object("jobReport")
            .stringType("id")
            .stringType("status", "RUNNING")
            .closeObject();

    return builder
        .given("An existing landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${deleteJobId}",
            "/api/landingzones/v1/azure/%s/delete-result/%s".formatted(landingZoneId, asyncJobId))
        .willRespondWith()
        .body(deleteResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact deleteLandingZone_notAuthorized(PactDslWithProvider builder) {
    var deleteRequestShape =
        new PactDslJsonBody().object("jobControl").stringType("id").closeObject();

    return builder
        .uponReceiving("An unauthorized request to create a landing zone")
        .method("POST")
        .pathFromProviderState(
            "/api/landingzones/v1/azure${landingZoneId}",
            "/api/landingzones/v1/azure/%s".formatted(landingZoneId))
        .body(deleteRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact landingZoneNotFound(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request for a non-existent landing zone")
        .method("GET")
        .path(String.format("/api/landingzones/v1/azure/%s", landingZoneId))
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
            .date("createdDate")
            .closeObject()
            .closeArray();

    return builder
        .given("A landing zone linked to a billing profile")
        .uponReceiving("A request to list landing zones for a billing profile")
        .method("GET")
        .path("/api/landingzones/v1/azure")
        .query(String.format("billingProfileId=%s", billingProfileId))
        .willRespondWith()
        .body(listResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact listLandingZones(PactDslWithProvider builder) {
    var listResponseShape =
        new PactDslJsonBody()
            .array("landingzones")
            .object()
            .uuid("landingZoneId")
            .uuid("billingProfileId")
            .stringType("definition")
            .stringType("version")
            .stringType("region")
            .date("createdDate")
            .closeObject()
            .closeArray();

    return builder
        .uponReceiving("A request to list all landing zones")
        .method("GET")
        .path("/api/landingzones/v1/azure")
        .willRespondWith()
        .body(listResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "startCreateLandingZone", pactVersion = PactSpecVersion.V3)
  void testStartCreateLandingZone() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneCreationJob(
            new BearerToken("fake"),
            "jobId",
            UUID.randomUUID(),
            "LZDefinition",
            "v1",
            List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
            UUID.randomUUID(),
            "https://example.com/async");

    assertNotNull(result);
    assertNotNull(result.getJobReport());
  }

  @Test
  @PactTestFor(pactMethod = "createLandingZone_notAuthorized", pactVersion = PactSpecVersion.V3)
  void testStartCreateLandignZone_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.startLandingZoneCreationJob(
                new BearerToken("fake"),
                "jobId",
                UUID.randomUUID(),
                "LZDefinition",
                "v1",
                List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
                UUID.randomUUID(),
                "https://example.com/async"));
  }

  @Test
  @PactTestFor(pactMethod = "landingZoneNotFound", pactVersion = PactSpecVersion.V3)
  void testLandingZoneNotFound() {
    assertThrows(
        LandingZoneServiceNotFoundException.class,
        () -> landingZoneService.getAzureLandingZone(new BearerToken("fake"), landingZoneId));
  }

  @Test
  @PactTestFor(pactMethod = "listLandingZonesForBillingProfile", pactVersion = PactSpecVersion.V3)
  void testListLandingZonesForBillingProfile() throws InterruptedException {
    var result =
        landingZoneService.listLandingZonesByBillingProfile(
            new BearerToken("fake"), billingProfileId);

    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "listLandingZones", pactVersion = PactSpecVersion.V3)
  void testListLandingZones() throws InterruptedException {
    var result = landingZoneService.listLandingZonesByBillingProfile(new BearerToken("fake"), null);

    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "startDeleteLandingZone", pactVersion = PactSpecVersion.V3)
  void testDeleteLandingZone() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneDeletionJob(
            new BearerToken("fake"), "fakeJobId", landingZoneId, "http://example.com");

    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "deleteLandingZone_notAuthorized", pactVersion = PactSpecVersion.V3)
  void testDeleteLandingZone_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.startLandingZoneDeletionJob(
                new BearerToken("fake"), "fakeJobId", landingZoneId, "http://example.com"));
  }

  @Test
  @PactTestFor(pactMethod = "deleteLandingZoneResult", pactVersion = PactSpecVersion.V3)
  void testDeleteLandingZoneResult() throws InterruptedException {
    var result =
        landingZoneService.getDeleteLandingZoneResult(
            new BearerToken("fake"), landingZoneId, asyncJobId.toString());
    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "createLandingZoneResult", pactVersion = PactSpecVersion.V3)
  void testCreateLandingZoneResult() throws InterruptedException {
    var result =
        landingZoneService.getAsyncJobResult(new BearerToken("fake"), asyncJobId.toString());
    assertNotNull(result);
  }
}
