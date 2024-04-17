package bio.terra.workspace.amalgam.landingzone.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.ErrorReport;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.model.DeletedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneCreation;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneDeletion;
import bio.terra.lz.futureservice.model.AzureLandingZone;
import bio.terra.lz.futureservice.model.AzureLandingZoneDeployedResource;
import bio.terra.lz.futureservice.model.AzureLandingZoneDetails;
import bio.terra.lz.futureservice.model.AzureLandingZoneResourcesList;
import bio.terra.lz.futureservice.model.AzureLandingZoneResourcesPurposeGroup;
import bio.terra.lz.futureservice.model.AzureLandingZoneResult;
import bio.terra.lz.futureservice.model.CreateLandingZoneResult;
import bio.terra.lz.futureservice.model.DeleteAzureLandingZoneJobResult;
import bio.terra.lz.futureservice.model.DeleteAzureLandingZoneResult;
import bio.terra.lz.futureservice.model.ResourceQuota;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class LandingApiClientTypeAdapterTest {

  @Test
  void toApiCreateLandingZoneResult() {
    LandingApiClientTypeAdapter adapter = new LandingApiClientTypeAdapter();
    var input =
        new LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation>()
            .result(new StartLandingZoneCreation(UUID.randomUUID(), "definition", "v1"))
            .jobReport(amalgamJobReport())
            .errorReport(amalgamErrorReport());

    var result = adapter.toApiCreateLandingZoneResult(input);

    assertThat(result.getLandingZoneId(), equalTo(input.getResult().landingZoneId()));
    assertThat(result.getDefinition(), equalTo("definition"));
    assertThat(result.getVersion(), equalTo("v1"));
    assertAmalgamatedJobReport(input.getJobReport(), result.getJobReport());
    assertAmalgamatedErrorReport(input.getApiErrorReport(), result.getErrorReport());
    ;
  }

  @Test
  void testToApiCreateLandingZoneResult() {
    LandingApiClientTypeAdapter adapter = new LandingApiClientTypeAdapter();

    var input =
        new CreateLandingZoneResult()
            .landingZoneId(UUID.randomUUID())
            .definition("definition")
            .version("v1")
            .jobReport(lzApiJobReport())
            .errorReport(lzApiErrorReport());

    var result = adapter.toApiCreateLandingZoneResult(input);

    assertThat(result.getLandingZoneId(), equalTo(input.getLandingZoneId()));
    assertThat(result.getDefinition(), equalTo("definition"));
    assertThat(result.getVersion(), equalTo("v1"));
    assertJobReport(input.getJobReport(), result.getJobReport());
    assertErrorReport(input.getErrorReport(), result.getErrorReport());
  }

  @Test
  void toApiAzureLandingZone() {
    var input =
        new LandingZone(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "definition",
            "v1",
            "US",
            new Date().toInstant().atOffset(ZoneOffset.UTC));

    var result = new LandingApiClientTypeAdapter().toApiAzureLandingZone(input);

    assertThat(result.getLandingZoneId(), equalTo(input.landingZoneId()));
    assertThat(result.getDefinition(), equalTo("definition"));
    assertThat(result.getVersion(), equalTo("v1"));
    assertThat(result.getBillingProfileId(), equalTo(input.billingProfileId()));
    assertThat(result.getCreatedDate(), equalTo(input.createdDate()));
  }

  @Test
  void toApiDeleteAzureLandingZoneResult() {
    var input =
        new LandingZoneJobService.AsyncJobResult<StartLandingZoneDeletion>()
            .result(new StartLandingZoneDeletion(UUID.randomUUID()))
            .jobReport(amalgamJobReport())
            .errorReport(amalgamErrorReport());

    var result = new LandingApiClientTypeAdapter().toApiDeleteAzureLandingZoneResult(input);

    assertThat(result.getLandingZoneId(), equalTo(input.getResult().landingZoneId()));
    assertAmalgamatedJobReport(input.getJobReport(), result.getJobReport());
    assertAmalgamatedErrorReport(input.getApiErrorReport(), result.getErrorReport());
  }

  @Test
  void testToApiDeleteAzureLandingZoneResult() {
    var input =
        new DeleteAzureLandingZoneResult()
            .landingZoneId(UUID.randomUUID())
            .jobReport(lzApiJobReport())
            .errorReport(lzApiErrorReport());

    var result = new LandingApiClientTypeAdapter().toApiDeleteAzureLandingZoneResult(input);

    assertThat(result.getLandingZoneId(), equalTo(input.getLandingZoneId()));
    assertJobReport(input.getJobReport(), result.getJobReport());
    assertErrorReport(input.getErrorReport(), result.getErrorReport());
  }

  @Test
  void toApiAzureLandingZoneDeployedResource() {
    var inputResource =
        new LandingZoneResource(
            UUID.randomUUID().toString(),
            "FAKE_TYPE",
            Map.of(),
            "us-east",
            Optional.of("name"),
            Optional.of("parent"));

    var result =
        new LandingApiClientTypeAdapter()
            .toApiAzureLandingZoneDeployedResource(inputResource, ResourcePurpose.SHARED_RESOURCE);

    assertThat(result.getResourceId(), equalTo(inputResource.resourceId()));
    assertThat(result.getResourceType(), equalTo(inputResource.resourceType()));
    assertThat(result.getRegion(), equalTo(inputResource.region()));
    assertThat(result.getTags(), equalTo(inputResource.tags()));
  }

  @Test
  void toApiAzureLandingZoneDeployedResource_subnet() {
    var inputResource =
        new LandingZoneResource(
            UUID.randomUUID().toString(),
            "FAKE_TYPE",
            Map.of(),
            "us-east",
            Optional.of("name"),
            Optional.of("parent"));

    var result =
        new LandingApiClientTypeAdapter()
            .toApiAzureLandingZoneDeployedResource(
                inputResource, SubnetResourcePurpose.WORKSPACE_BATCH_SUBNET);

    assertThat(result.getResourceId(), equalTo(inputResource.resourceId()));
    assertThat(result.getResourceType(), equalTo(inputResource.resourceType()));
    assertThat(result.getRegion(), equalTo(inputResource.region()));
    assertThat(result.getTags(), equalTo(inputResource.tags()));
    assertThat(result.getResourceName(), equalTo(inputResource.resourceName().orElseThrow()));
  }

  @Test
  void toApiAzureLandingZoneDeployedResource_failsOnUnsupportedPurpose() {
    var inputResource =
        new LandingZoneResource(
            UUID.randomUUID().toString(),
            "FAKE_TYPE",
            Map.of(),
            "us-east",
            Optional.of("name"),
            Optional.of("parent"));

    var adapter = new LandingApiClientTypeAdapter();
    assertThrows(
        LandingZoneUnsupportedPurposeException.class,
        () ->
            adapter.toApiAzureLandingZoneDeployedResource(
                inputResource, new LandingZonePurpose() {}));
  }

  @Test
  void toApiDeleteAzureLandingZoneJobResult() {
    var input =
        new LandingZoneJobService.AsyncJobResult<DeletedLandingZone>()
            .result(
                new DeletedLandingZone(
                    UUID.randomUUID(), List.of("resource1", "resource2"), UUID.randomUUID()))
            .jobReport(amalgamJobReport())
            .errorReport(amalgamErrorReport());

    var result = new LandingApiClientTypeAdapter().toApiDeleteAzureLandingZoneJobResult(input);

    assertAmalgamatedJobReport(input.getJobReport(), result.getJobReport());
    assertAmalgamatedErrorReport(input.getApiErrorReport(), result.getErrorReport());
    assertThat(result.getLandingZoneId(), equalTo(input.getResult().landingZoneId()));
    assertThat(result.getResources(), equalTo(input.getResult().deleteResources()));
  }

  @Test
  void testToApiDeleteAzureLandingZoneJobResult() {
    var input =
        new DeleteAzureLandingZoneJobResult()
            .jobReport(lzApiJobReport())
            .errorReport(lzApiErrorReport())
            .landingZoneId(UUID.randomUUID())
            .resources(List.of("resource1", "resource2"));

    var result = new LandingApiClientTypeAdapter().toApiDeleteAzureLandingZoneJobResult(input);

    assertJobReport(input.getJobReport(), result.getJobReport());
    assertErrorReport(input.getErrorReport(), result.getErrorReport());
    assertThat(result.getLandingZoneId(), equalTo(result.getLandingZoneId()));
    assertThat(result.getResources(), equalTo(input.getResources()));
  }

  @Test
  void toApiResourceQuota() {
    var input =
        new ResourceQuota()
            .azureResourceId("fake_resource_id")
            .landingZoneId(UUID.randomUUID())
            .resourceType("fake_resource_type")
            .quotaValues(Map.of("foo", "a", "bar", "b"));

    var result =
        new LandingApiClientTypeAdapter().toApiResourceQuota(input.getLandingZoneId(), input);

    assertThat(result.getLandingZoneId(), equalTo(input.getLandingZoneId()));
    assertThat(result.getResourceType(), equalTo(input.getResourceType()));
    assertThat(result.getAzureResourceId(), equalTo(input.getAzureResourceId()));
    assertThat(result.getQuotaValues(), equalTo(input.getQuotaValues()));
  }

  @Test
  void testToApiResourceQuota() {
    var lzId = UUID.randomUUID();
    var input =
        new bio.terra.landingzone.library.landingzones.management.quotas.ResourceQuota(
            "fake_resource_id", "fake_resource_type", Map.of("foo", "a", "bar", "b"));

    var result = new LandingApiClientTypeAdapter().toApiResourceQuota(lzId, input);

    assertThat(result.getLandingZoneId(), equalTo(lzId));
    assertThat(result.getResourceType(), equalTo(input.resourceType()));
    assertThat(result.getAzureResourceId(), equalTo(input.resourceId()));
    assertThat(result.getQuotaValues(), equalTo(input.quota()));
  }

  @Test
  void toApiAzureLandingZoneResult() {
    var resources =
        List.of(
            new LandingZoneResource(
                "fake_resource_1",
                "fake",
                Map.of("key1", "value1"),
                "us-east",
                Optional.of("resourceName"),
                Optional.of("parent")));
    var input =
        new LandingZoneJobService.AsyncJobResult<DeployedLandingZone>()
            .jobReport(amalgamJobReport())
            .errorReport(amalgamErrorReport())
            .result(new DeployedLandingZone(UUID.randomUUID(), resources));

    var result = new LandingApiClientTypeAdapter().toApiAzureLandingZoneResult(input);

    assertAmalgamatedErrorReport(input.getApiErrorReport(), result.getErrorReport());
    assertAmalgamatedJobReport(input.getJobReport(), result.getJobReport());
    assertThat(input.getResult().id(), equalTo(result.getLandingZone().getId()));
    assertThat(
        resources.get(0).resourceId(),
        equalTo(result.getLandingZone().getResources().get(0).getResourceId()));
    assertThat(
        resources.get(0).resourceType(),
        equalTo(result.getLandingZone().getResources().get(0).getResourceType()));
    assertThat(
        resources.get(0).region(),
        equalTo(result.getLandingZone().getResources().get(0).getRegion()));
    assertThat(
        resources.get(0).tags(), equalTo(result.getLandingZone().getResources().get(0).getTags()));
  }

  @Test
  void testToApiAzureLandingZoneResult() {
    var resources =
        List.of(
            new AzureLandingZoneDeployedResource()
                .resourceType("resource_type")
                .resourceName("resource_name")
                .resourceId("resource_id")
                .tags(Map.of("key", "value")));
    var input =
        new AzureLandingZoneResult()
            .errorReport(lzApiErrorReport())
            .jobReport(lzApiJobReport())
            .landingZone(new AzureLandingZoneDetails().id(UUID.randomUUID()).resources(resources));

    var result = new LandingApiClientTypeAdapter().toApiAzureLandingZoneResult(input);
    assertErrorReport(input.getErrorReport(), result.getErrorReport());
    assertJobReport(input.getJobReport(), result.getJobReport());
    assertThat(input.getLandingZone().getId(), equalTo(result.getLandingZone().getId()));
    assertThat(
        resources.get(0).getResourceId(),
        equalTo(result.getLandingZone().getResources().get(0).getResourceId()));
    assertThat(
        resources.get(0).getResourceType(),
        equalTo(result.getLandingZone().getResources().get(0).getResourceType()));
    assertThat(
        resources.get(0).getRegion(),
        equalTo(result.getLandingZone().getResources().get(0).getRegion()));
    assertThat(
        resources.get(0).getTags(),
        equalTo(result.getLandingZone().getResources().get(0).getTags()));
  }

  @Test
  void testToApiAzureLandingZone() {
    var input =
        new AzureLandingZone()
            .landingZoneId(UUID.randomUUID())
            .definition("definition")
            .version("v1")
            .region("us-east")
            .createdDate(new Date());

    var result = new LandingApiClientTypeAdapter().toApiAzureLandingZone(input);

    assertThat(input.getLandingZoneId(), equalTo(result.getLandingZoneId()));
    assertThat(input.getDefinition(), equalTo(result.getDefinition()));
    assertThat(input.getVersion(), equalTo(result.getVersion()));
    assertThat(input.getRegion(), equalTo(result.getRegion()));
    assertThat(input.getCreatedDate().toInstant(), equalTo(result.getCreatedDate().toInstant()));
  }

  @Test
  void toApiResourcesList() {
    var input =
        new AzureLandingZoneResourcesList()
            .resources(
                List.of(
                    new AzureLandingZoneResourcesPurposeGroup()
                        .purpose("purpose1")
                        .deployedResources(
                            List.of(
                                new AzureLandingZoneDeployedResource()
                                    .resourceType("resource_type")))));

    var result = new LandingApiClientTypeAdapter().toApiResourcesList(input);

    var resultDeployedResources = result.getResources().get(0);
    var inputDeployedResources = input.getResources().get(0).getDeployedResources();

    assertThat(
        resultDeployedResources.getDeployedResources().get(0),
        equalTo(inputDeployedResources.get(0).getResourceName()));
  }

  @Test
  void testToApiResourcesList() {}

  private JobReport amalgamJobReport() {
    return new JobReport()
        .id(UUID.randomUUID().toString())
        .resultURL("fake")
        .completed(Instant.now().toString())
        .status(JobReport.StatusEnum.SUCCEEDED);
  }

  private ErrorReport amalgamErrorReport() {
    return new ErrorReport().statusCode(501).message("failed").causes(List.of("cause1", "cause2"));
  }

  private bio.terra.lz.futureservice.model.JobReport lzApiJobReport() {
    return new bio.terra.lz.futureservice.model.JobReport()
        .completed(Instant.now().toString())
        .id(UUID.randomUUID().toString())
        .status(bio.terra.lz.futureservice.model.JobReport.StatusEnum.SUCCEEDED)
        .resultURL("fake");
  }

  private bio.terra.lz.futureservice.model.ErrorReport lzApiErrorReport() {
    return new bio.terra.lz.futureservice.model.ErrorReport()
        .statusCode(501)
        .message("failed")
        .causes(List.of("cause1", "cause2"));
  }

  private void assertJobReport(
      bio.terra.lz.futureservice.model.JobReport input, ApiJobReport result) {
    assertThat(result.getId(), equalTo(input.getId()));
    assertThat(result.getId(), equalTo(input.getId()));
    assertThat(result.getStatus(), equalTo(ApiJobReport.StatusEnum.SUCCEEDED));
    assertThat(result.getResultURL(), equalTo(input.getResultURL()));
    assertThat(result.getCompleted(), equalTo(input.getCompleted()));
  }

  private void assertAmalgamatedJobReport(JobReport input, ApiJobReport result) {
    assertThat(input.getId(), equalTo(result.getId()));
    assertThat(input.getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    assertThat(input.getResultURL(), equalTo(result.getResultURL()));
    assertThat(input.getCompleted(), equalTo(result.getCompleted()));
  }

  private void assertAmalgamatedErrorReport(ErrorReport input, ApiErrorReport result) {
    assertThat(input.getStatusCode(), equalTo(result.getStatusCode()));
    assertThat(input.getMessage(), equalTo(result.getMessage()));
    assertThat(input.getCauses(), equalTo(result.getCauses()));
  }

  private void assertErrorReport(
      bio.terra.lz.futureservice.model.ErrorReport input, ApiErrorReport result) {
    assertThat(result.getStatusCode(), equalTo(input.getStatusCode()));
    assertThat(result.getMessage(), equalTo(input.getMessage()));
    assertThat(result.getCauses(), equalTo(input.getCauses()));
  }
}
