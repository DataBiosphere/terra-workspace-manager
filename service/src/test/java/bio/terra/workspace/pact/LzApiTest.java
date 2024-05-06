package bio.terra.workspace.pact;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import bio.terra.lz.futureservice.model.JobReport;
import bio.terra.workspace.amalgam.landingzone.azure.HttpLandingZoneService;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceAuthorizationException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceNotFoundException;
import bio.terra.workspace.app.configuration.external.LandingZoneServiceConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
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
  static final UUID azureResourceId = UUID.fromString("e3327645-1e8f-454d-bdae-fe06b4762542");

  private DslPart buildJobReportShape(JobReport.StatusEnum jobStatus) {
    var shape =
        new PactDslJsonBody()
            .stringType("id")
            .stringType("description")
            .stringMatcher("status", "RUNNING|SUCCEEDED|FAILED")
            .integerType("statusCode")
            .datetime("submitted")
            .stringType("resultURL");
    if (jobStatus == JobReport.StatusEnum.SUCCEEDED || jobStatus == JobReport.StatusEnum.FAILED) {
      shape.datetime("completed");
    }
    return shape;
  }

  private DslPart buildErrorReportShape() {
    return new PactDslJsonBody().stringType("message").integerType("statusCode");
  }

  static DslPart jobControlShape = new PactDslJsonBody().stringType("id");

  static DslPart landingZoneCreateRequestShape =
      new PactDslJsonBody()
          .uuid("landingZoneId")
          .stringType("definition")
          .stringType("version")
          .uuid("billingProfileId")
          .object("jobControl", jobControlShape)
          .eachLike("parameters")
          .stringType("key")
          .stringType("value")
          .closeArray();

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

  private DslPart buildCreateResponse(JobReport.StatusEnum jobStatus) {
    var createResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId")
            .stringType("definition")
            .stringType("version")
            .object("jobReport", buildJobReportShape(jobStatus));

    if (jobStatus == JobReport.StatusEnum.FAILED) {
      createResponseShape.object("errorReport", buildErrorReportShape());
    }
    return createResponseShape;
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact submitLandingZoneCreate_success(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .body(buildCreateResponse(JobReport.StatusEnum.SUCCEEDED))
        .status(HttpStatus.ACCEPTED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact submitLandingZoneCreate_error(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(contentTypeJsonHeader)
        .willRespondWith()
        .body(buildCreateResponse(JobReport.StatusEnum.FAILED))
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
            .object("jobReport", buildJobReportShape(JobReport.StatusEnum.SUCCEEDED))
            .object("landingZone")
            .uuid("id")
            .eachLike("resources")
            .stringType("resourceId")
            .stringType("resourceType")
            .stringType("resourceName")
            .stringType("resourceId")
            .stringType("region")
            .object("tags");

    return builder
        .given("An existing landing zone creation job")
        .uponReceiving("A request to get the landing zone creation job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/create-result/${asyncJobId}",
            "/api/landingzones/v1/azure/create-result/%s".formatted(asyncJobId))
        .willRespondWith()
        .body(createResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact createLandingZoneResult_notAuthorized(PactDslWithProvider builder) {
    return builder
        .given("An existing landing zone creation job")
        .uponReceiving("A request to get the landing zone creation job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure//create-result/${asyncJobId}",
            "/api/landingzones/v1/azure/create-result/%s".formatted(asyncJobId))
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startDeleteLandingZone(PactDslWithProvider builder) {
    var deleteRequestShape =
        new PactDslJsonBody().object("jobControl").stringType("id").closeObject();

    var deleteResponseShape =
        new PactDslJsonBody()
            .object("jobReport", buildJobReportShape(JobReport.StatusEnum.RUNNING));

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
  public RequestResponsePact deleteLandingZoneResult_success(PactDslWithProvider builder) {
    var deleteResultResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId", landingZoneId)
            .object("jobReport", buildJobReportShape(JobReport.StatusEnum.SUCCEEDED));

    return builder
        .given("An existing successful landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${asyncJobId}",
            "/api/landingzones/v1/azure/%s/delete-result/%s".formatted(landingZoneId, asyncJobId))
        .willRespondWith()
        .body(deleteResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact deleteLandingZoneResult_failed(PactDslWithProvider builder) {

    var jobReport = buildJobReportShape(JobReport.StatusEnum.FAILED);
    var errorReportShape = buildErrorReportShape();

    var deleteResultResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId", landingZoneId)
            .object("jobReport", buildJobReportShape(JobReport.StatusEnum.FAILED))
            .object("errorReport", errorReportShape);

    return builder
        .given("An existing failed landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${asyncJobId}",
            "/api/landingzones/v1/azure/%s/delete-result/%s".formatted(landingZoneId, asyncJobId))
        .willRespondWith()
        .body(deleteResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact deleteLandingZoneResult_notAuthorized(PactDslWithProvider builder) {
    return builder
        .given("An existing landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${asyncJobId}",
            "/api/landingzones/v1/azure/%s/delete-result/%s".formatted(landingZoneId, asyncJobId))
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact deleteLandingZone_notAuthorized(PactDslWithProvider builder) {
    var deleteRequestShape = new PactDslJsonBody().object("jobControl", jobControlShape);

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
            "/api/landingzones/v1/azure/${landingZoneId}",
            "/api/landingzones/v1/azure/%s".formatted(landingZoneId))
        .willRespondWith()
        .body(getResultResponseShape)
        .status(HttpStatus.OK.value())
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
            .date("createdDate");

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
            "/api/landingzones/v1/azure/${landingZoneId}/resources",
            "/api/landingzones/v1/azure/%s/resources".formatted(landingZoneId))
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
            "/api/landingzones/v1/azure/${landingZoneId}/resource-quota",
            "/api/landingzones/v1/azure/%s/resource-quota".formatted(landingZoneId))
        .query(String.format("azureResourceId=%s", azureResourceId))
        .willRespondWith()
        .body(quotaResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "submitLandingZoneCreate_success", pactVersion = PactSpecVersion.V3)
  void startCreateLandingZone() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneCreationJob(
            new BearerToken("fake"),
            "jobId",
            asyncJobId,
            "LZDefinition",
            "v1",
            List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
            billingProfileId,
            "https://example.com/async");

    assertNotNull(result);
    assertNotNull(result.getJobReport());
  }

  @Test
  @PactTestFor(pactMethod = "submitLandingZoneCreate_error", pactVersion = PactSpecVersion.V3)
  void startCreateLandingZone_error() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneCreationJob(
            new BearerToken("fake"),
            "jobId",
            asyncJobId,
            "LZDefinition",
            "v1",
            List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
            billingProfileId,
            "https://example.com/async");

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertErrorReport(result.getErrorReport());
  }

  @Test
  @PactTestFor(pactMethod = "createLandingZone_notAuthorized", pactVersion = PactSpecVersion.V3)
  void startCreateLandignZone_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.startLandingZoneCreationJob(
                new BearerToken("fake"),
                "jobId",
                asyncJobId,
                "LZDefinition",
                "v1",
                List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
                billingProfileId,
                "https://example.com/async"));
  }

  @Test
  @PactTestFor(pactMethod = "landingZoneNotFound", pactVersion = PactSpecVersion.V3)
  void landingZoneNotFound() {
    assertThrows(
        LandingZoneServiceNotFoundException.class,
        () -> landingZoneService.getAzureLandingZone(new BearerToken("fake"), landingZoneId));
  }

  @Test
  @PactTestFor(pactMethod = "listLandingZonesForBillingProfile", pactVersion = PactSpecVersion.V3)
  void listLandingZonesForBillingProfile() throws InterruptedException {
    var result =
        landingZoneService.listLandingZonesByBillingProfile(
            new BearerToken("fake"), billingProfileId);

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
  @PactTestFor(pactMethod = "startDeleteLandingZone", pactVersion = PactSpecVersion.V3)
  void deleteLandingZone() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneDeletionJob(
            new BearerToken("fake"), "fakeJobId", landingZoneId, "http://example.com");

    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "deleteLandingZone_notAuthorized", pactVersion = PactSpecVersion.V3)
  void deleteLandingZone_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.startLandingZoneDeletionJob(
                new BearerToken("fake"), "fakeJobId", landingZoneId, "http://example.com"));
  }

  @Test
  @PactTestFor(pactMethod = "deleteLandingZoneResult_success", pactVersion = PactSpecVersion.V3)
  void deleteLandingZoneResult() throws InterruptedException {
    var result =
        landingZoneService.getDeleteLandingZoneResult(
            new BearerToken("fake"), landingZoneId, asyncJobId.toString());

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertNull(result.getErrorReport());
  }

  @Test
  @PactTestFor(pactMethod = "deleteLandingZoneResult_failed", pactVersion = PactSpecVersion.V3)
  void fetchDeleteLandingZoneResult_failed() throws InterruptedException {
    var result =
        landingZoneService.getDeleteLandingZoneResult(
            new BearerToken("fake"), landingZoneId, asyncJobId.toString());

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertErrorReport(result.getErrorReport());
  }

  @Test
  @PactTestFor(
      pactMethod = "deleteLandingZoneResult_notAuthorized",
      pactVersion = PactSpecVersion.V3)
  void deleteLandingZoneResult_notAuthorized() throws InterruptedException {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.getDeleteLandingZoneResult(
                new BearerToken("fake"), landingZoneId, asyncJobId.toString()));
  }

  @Test
  @PactTestFor(pactMethod = "createLandingZoneResult", pactVersion = PactSpecVersion.V3)
  void createLandingZoneResult() throws InterruptedException {
    var result =
        landingZoneService.getAsyncJobResult(new BearerToken("fake"), asyncJobId.toString());

    assertNotNull(result);
    assertJobReport(result.getJobReport());
  }

  @Test
  @PactTestFor(
      pactMethod = "createLandingZoneResult_notAuthorized",
      pactVersion = PactSpecVersion.V3)
  void createLandingZoneResult_notAuthorized() throws InterruptedException {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () -> landingZoneService.getAsyncJobResult(new BearerToken("fake"), asyncJobId.toString()));
  }

  @Test
  @PactTestFor(pactMethod = "getLandingZone", pactVersion = PactSpecVersion.V3)
  void getLandingZoneRegion() throws InterruptedException {
    var result = landingZoneService.getLandingZoneRegion(new BearerToken("fake"), landingZoneId);

    assertNotNull(result);
  }

  @Test
  @PactTestFor(pactMethod = "getLandingZone", pactVersion = PactSpecVersion.V3)
  void getAzureLandingZone() throws InterruptedException {
    var result = landingZoneService.getAzureLandingZone(new BearerToken("fake"), landingZoneId);

    assertNotNull(result);
    assertNotNull(result.getCreatedDate());
    assertNotNull(result.getBillingProfileId());
    assertNotNull(result.getRegion());
    assertNotNull(result.getVersion());
    assertNotNull(result.getLandingZoneId());
    assertNotNull(result.getDefinition());
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
        landingZoneService.listResourcesWithPurposes(new BearerToken("fake"), landingZoneId);

    assertNotNull(result);
    assertThat(result.getResources(), is(not(empty())));
    assertThat(result.getResources().get(0).getDeployedResources(), is(not(empty())));
  }

  @Test
  @PactTestFor(pactMethod = "listAzureLandingZoneResources", pactVersion = PactSpecVersion.V3)
  void listLandingZoneResourcesMatchingPurpose() throws InterruptedException {
    var result =
        landingZoneService.listResourcesMatchingPurpose(
            new BearerToken("fake"), landingZoneId, ResourcePurpose.SHARED_RESOURCE);

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
            new BearerToken("fake"), landingZoneId, azureResourceId.toString());

    assertNotNull(result);
    assertNotNull(result.getAzureResourceId());
    assertNotNull(result.getResourceType());
    assertNotNull(result.getLandingZoneId());
    assertNotNull(result.getQuotaValues());
    assertThat(result.getQuotaValues(), is(anEmptyMap()));
  }

  void assertJobReport(ApiJobReport jobReport) {
    assertNotNull(jobReport);
    assertNotNull(jobReport.getId());
    assertNotNull(jobReport.getCompleted());
    assertNotNull(jobReport.getDescription());
    assertNotNull(jobReport.getStatus());
    assertNotNull(jobReport.getSubmitted());
  }

  void assertErrorReport(ApiErrorReport errorReport) {
    assertNotNull(errorReport);
    assertNotNull(errorReport.getMessage());
  }
}
