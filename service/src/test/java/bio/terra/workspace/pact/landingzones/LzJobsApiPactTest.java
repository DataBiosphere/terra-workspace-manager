package bio.terra.workspace.pact.landingzones;

import static bio.terra.workspace.pact.PactFixtures.ASYNC_JOB_ID;
import static bio.terra.workspace.pact.PactFixtures.BILLING_PROFILE_ID;
import static bio.terra.workspace.pact.PactFixtures.CONTENT_TYPE_JSON_HEADER;
import static bio.terra.workspace.pact.PactFixtures.LANDING_ZONE_ID;
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
import bio.terra.lz.futureservice.model.JobReport;
import bio.terra.workspace.amalgam.landingzone.azure.HttpLandingZoneService;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceAuthorizationException;
import bio.terra.workspace.app.configuration.external.LandingZoneServiceConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

/** Exercises LZS APIs concerned with job dispatch and async polling (creation, deletion, etc.) */
@Tag("pact-test")
@ExtendWith(PactConsumerTestExt.class)
public class LzJobsApiPactTest {

  HttpLandingZoneService landingZoneService;

  DslPart buildJobReportShape(JobReport.StatusEnum jobStatus) {
    var shape =
        new PactDslJsonBody()
            .stringType("id")
            .stringType("description")
            .stringMatcher("status",  jobStatus.getValue())
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
  public RequestResponsePact startCreateLandingZone_running(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .body(buildCreateResponse(JobReport.StatusEnum.RUNNING))
        .status(HttpStatus.ACCEPTED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startCreateLandingZone_failed(PactDslWithProvider builder) {
    return builder
        .uponReceiving("A request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .body(buildCreateResponse(JobReport.StatusEnum.FAILED))
        .status(HttpStatus.ACCEPTED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startCreateLandingZone_notAuthorized(PactDslWithProvider builder) {
    return builder
        .uponReceiving("An unauthorized request to create a landing zone")
        .method("POST")
        .path("/api/landingzones/v1/azure")
        .body(landingZoneCreateRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getCreateLandingZoneResult(PactDslWithProvider builder) {
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
            "/api/landingzones/v1/azure/create-result/${ASYNC_JOB_ID}",
            "/api/landingzones/v1/azure/create-result/%s".formatted(ASYNC_JOB_ID))
        .willRespondWith()
        .body(createResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getCreateLandingZoneResult_notAuthorized(PactDslWithProvider builder) {
    return builder
        .given("An existing landing zone creation job")
        .uponReceiving("A request to get the landing zone creation job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure//create-result/${ASYNC_JOB_ID}",
            "/api/landingzones/v1/azure/create-result/%s".formatted(ASYNC_JOB_ID))
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startDeleteLandingZone_running(PactDslWithProvider builder) {
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
            "/api/landingzones/v1/azure/%s".formatted(LANDING_ZONE_ID))
        .body(deleteRequestShape)
        .headers(CONTENT_TYPE_JSON_HEADER)
        .willRespondWith()
        .body(deleteResponseShape)
        .status(HttpStatus.ACCEPTED.value())
        .toPact();
  }


  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact startDeleteLandingZone_notAuthorized(PactDslWithProvider builder) {
    var deleteRequestShape = new PactDslJsonBody().object("jobControl", jobControlShape);

    return builder
            .uponReceiving("An unauthorized request to delete a landing zone")
            .method("POST")
            .pathFromProviderState(
                    "/api/landingzones/v1/azure${landingZoneId}",
                    "/api/landingzones/v1/azure/%s".formatted(LANDING_ZONE_ID))
            .body(deleteRequestShape)
            .headers(CONTENT_TYPE_JSON_HEADER)
            .willRespondWith()
            .status(HttpStatus.UNAUTHORIZED.value())
            .toPact();
  }


  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getDeleteLandingZoneResult_success(PactDslWithProvider builder) {
    var deleteResultResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId", LANDING_ZONE_ID)
            .object("jobReport", buildJobReportShape(JobReport.StatusEnum.SUCCEEDED));

    return builder
        .given("An existing successful landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${ASYNC_JOB_ID}",
            "/api/landingzones/v1/azure/%s/delete-result/%s"
                .formatted(LANDING_ZONE_ID, ASYNC_JOB_ID))
        .willRespondWith()
        .body(deleteResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getDeleteLandingZoneResult_failed(PactDslWithProvider builder) {
    var deleteResultResponseShape =
        new PactDslJsonBody()
            .uuid("landingZoneId", LANDING_ZONE_ID)
            .object("jobReport", buildJobReportShape(JobReport.StatusEnum.FAILED))
            .object("errorReport", buildErrorReportShape());

    return builder
        .given("An existing failed landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${ASYNC_JOB_ID}",
            "/api/landingzones/v1/azure/%s/delete-result/%s"
                .formatted(LANDING_ZONE_ID, ASYNC_JOB_ID))
        .willRespondWith()
        .body(deleteResultResponseShape)
        .status(HttpStatus.OK.value())
        .toPact();
  }

  @Pact(consumer = "workspacemanager", provider = "lzs")
  public RequestResponsePact getDeleteLandingZoneResult_notAuthorized(PactDslWithProvider builder) {
    return builder
        .given("An existing landing zone deletion job")
        .uponReceiving("A request to get the landing zone deletion job result")
        .method("GET")
        .pathFromProviderState(
            "/api/landingzones/v1/azure/${landingZoneId}/delete-result/${ASYNC_JOB_ID}",
            "/api/landingzones/v1/azure/%s/delete-result/%s"
                .formatted(LANDING_ZONE_ID, ASYNC_JOB_ID))
        .willRespondWith()
        .status(HttpStatus.UNAUTHORIZED.value())
        .toPact();
  }

  @BeforeEach
  void setup(MockServer mockServer) {
    var config = mock(LandingZoneServiceConfiguration.class);
    when(config.getBasePath()).thenReturn(mockServer.getUrl());
    landingZoneService = new HttpLandingZoneService(OpenTelemetry.noop(), config);
  }

  @Test
  @PactTestFor(pactMethod = "startCreateLandingZone_running", pactVersion = PactSpecVersion.V3)
  void startCreateLandingZone_running() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneCreationJob(
            new BearerToken("fake"),
            "jobId",
            ASYNC_JOB_ID,
            "LZDefinition",
            "v1",
            List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
            BILLING_PROFILE_ID,
            "https://example.com/async");

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertNull(result.getErrorReport());
  }

  @Test
  @PactTestFor(pactMethod = "startCreateLandingZone_failed", pactVersion = PactSpecVersion.V3)
  void startCreateLandingZone_failed() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneCreationJob(
            new BearerToken("fake"),
            "jobId",
            ASYNC_JOB_ID,
            "LZDefinition",
            "v1",
            List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
            BILLING_PROFILE_ID,
            "https://example.com/async");

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertErrorReport(result.getErrorReport());
  }

  @Test
  @PactTestFor(pactMethod = "startCreateLandingZone_notAuthorized", pactVersion = PactSpecVersion.V3)
  void startCreateLandingZone_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.startLandingZoneCreationJob(
                new BearerToken("fake"),
                "jobId",
                ASYNC_JOB_ID,
                "LZDefinition",
                "v1",
                List.of(new ApiAzureLandingZoneParameter().key("key").value("value")),
                BILLING_PROFILE_ID,
                "https://example.com/async"));
  }

  @Test
  @PactTestFor(pactMethod = "startDeleteLandingZone_running", pactVersion = PactSpecVersion.V3)
  void startDeleteLandingZone_running() throws InterruptedException {
    var result =
        landingZoneService.startLandingZoneDeletionJob(
            new BearerToken("fake"), "fakeJobId", LANDING_ZONE_ID, "http://example.com");

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertNull(result.getErrorReport());
  }

  @Test
  @PactTestFor(pactMethod = "startDeleteLandingZone_notAuthorized", pactVersion = PactSpecVersion.V3)
  void startDeleteLandingZone_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.startLandingZoneDeletionJob(
                new BearerToken("fake"), "fakeJobId", LANDING_ZONE_ID, "http://example.com"));
  }

  @Test
  @PactTestFor(pactMethod = "getDeleteLandingZoneResult_success", pactVersion = PactSpecVersion.V3)
  void getDeleteLandingZoneResult_success() throws InterruptedException {
    var result =
        landingZoneService.getDeleteLandingZoneResult(
            new BearerToken("fake"), LANDING_ZONE_ID, ASYNC_JOB_ID.toString());

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertNull(result.getErrorReport());
  }

  @Test
  @PactTestFor(pactMethod = "getDeleteLandingZoneResult_failed", pactVersion = PactSpecVersion.V3)
  void getDeleteLandingZoneResult_failed() throws InterruptedException {
    var result =
        landingZoneService.getDeleteLandingZoneResult(
            new BearerToken("fake"), LANDING_ZONE_ID, ASYNC_JOB_ID.toString());

    assertNotNull(result);
    assertJobReport(result.getJobReport());
    assertErrorReport(result.getErrorReport());
  }

  @Test
  @PactTestFor(
      pactMethod = "getDeleteLandingZoneResult_notAuthorized",
      pactVersion = PactSpecVersion.V3)
  void getDeleteLandingZoneResult_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.getDeleteLandingZoneResult(
                new BearerToken("fake"), LANDING_ZONE_ID, ASYNC_JOB_ID.toString()));
  }

  @Test
  @PactTestFor(pactMethod = "getCreateLandingZoneResult", pactVersion = PactSpecVersion.V3)
  void getCreateLandingZoneResult() throws InterruptedException {
    var result =
        landingZoneService.getAsyncJobResult(new BearerToken("fake"), ASYNC_JOB_ID.toString());

    assertNotNull(result);
    assertJobReport(result.getJobReport());
  }

  @Test
  @PactTestFor(
      pactMethod = "getCreateLandingZoneResult_notAuthorized",
      pactVersion = PactSpecVersion.V3)
  void getCreateLandingZoneResult_notAuthorized() {
    assertThrows(
        LandingZoneServiceAuthorizationException.class,
        () ->
            landingZoneService.getAsyncJobResult(new BearerToken("fake"), ASYNC_JOB_ID.toString()));
  }

  void assertJobReport(ApiJobReport jobReport) {
    assertNotNull(jobReport);
    assertNotNull(jobReport.getId());
    assertNotNull(jobReport.getDescription());
    assertNotNull(jobReport.getStatus());
    assertNotNull(jobReport.getSubmitted());
    if (jobReport.getStatus() == ApiJobReport.StatusEnum.SUCCEEDED || jobReport.getStatus() == ApiJobReport.StatusEnum.FAILED) {
      assertNotNull(jobReport.getCompleted());
    }
  }

  void assertErrorReport(ApiErrorReport errorReport) {
    assertNotNull(errorReport);
    assertNotNull(errorReport.getMessage());
  }
}
