package bio.terra.workspace.common.fixtures;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.model.DeletedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResourcesByPurpose;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneCreation;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneDeletion;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;

public class AzureLandingZoneFixtures {
  public static LandingZoneJobService.AsyncJobResult<DeployedLandingZone>
      createJobResultWithRunningState(String jobId) {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(JobReport.StatusEnum.RUNNING)
            .statusCode(HttpStatus.SC_ACCEPTED)
            .submitted(Instant.now().toString())
            .resultURL("create-result/"));
    return asyncJobResult;
  }

  public static LandingZoneJobService.AsyncJobResult<DeployedLandingZone>
      createJobResultWithSucceededState(String jobId, UUID landingZoneId) {
    LandingZoneJobService.AsyncJobResult<DeployedLandingZone> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(JobReport.StatusEnum.SUCCEEDED)
            .statusCode(HttpStatus.SC_OK)
            .submitted(Instant.now().toString())
            .resultURL("create-result/"));

    asyncJobResult.result(
        new DeployedLandingZone(
            landingZoneId,
            Collections.singletonList(
                new LandingZoneResource(
                    "resId",
                    "resourceType",
                    null,
                    "westus",
                    Optional.of("resourceName"),
                    Optional.empty()))));
    return asyncJobResult;
  }

  public static LandingZoneJobService.AsyncJobResult<DeletedLandingZone> createDeleteJobResult(
      String jobId, UUID landingZoneId, JobReport.StatusEnum jobStatus) {
    LandingZoneJobService.AsyncJobResult<DeletedLandingZone> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(jobStatus)
            .statusCode(HttpStatus.SC_OK)
            .submitted(Instant.now().toString())
            .resultURL("delete-result/"));

    asyncJobResult.result(new DeletedLandingZone(landingZoneId, List.of("resource/id")));
    return asyncJobResult;
  }

  public static LandingZoneJobService.AsyncJobResult<StartLandingZoneDeletion>
      createStartDeleteJobResultWithRunningState(String jobId, UUID landingZoneId) {
    LandingZoneJobService.AsyncJobResult<StartLandingZoneDeletion> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(JobReport.StatusEnum.RUNNING)
            .statusCode(HttpStatus.SC_OK)
            .submitted(Instant.now().toString())
            .resultURL("delete-result/"));

    asyncJobResult.result(new StartLandingZoneDeletion(landingZoneId));
    return asyncJobResult;
  }

  public static ApiCreateAzureLandingZoneRequestBody buildCreateAzureLandingZoneRequest(
      String jobId) {
    return new ApiCreateAzureLandingZoneRequestBody()
        .billingProfileId(UUID.randomUUID())
        .jobControl(new ApiJobControl().id(jobId))
        .definition("azureLandingZoneDefinition")
        .version("v1");
  }

  public static ApiDeleteAzureLandingZoneRequestBody buildDeleteAzureLandingZoneRequest(
      String jobId) {
    return new ApiDeleteAzureLandingZoneRequestBody().jobControl(new ApiJobControl().id(jobId));
  }

  public static ApiCreateAzureLandingZoneRequestBody
      buildCreateAzureLandingZoneRequestWithoutDefinition(String jobId) {
    return new ApiCreateAzureLandingZoneRequestBody()
        .billingProfileId(UUID.randomUUID())
        .jobControl(new ApiJobControl().id(jobId))
        .version("v1");
  }

  public static ApiCreateAzureLandingZoneRequestBody
      buildCreateAzureLandingZoneRequestWithoutTarget(String jobId) {
    return new ApiCreateAzureLandingZoneRequestBody()
        .jobControl(new ApiJobControl().id(jobId))
        .definition("azureLandingZoneDefinition")
        .version("v1");
  }

  public static LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation>
      createStartCreateJobResult(String jobId, JobReport.StatusEnum jobStatus, UUID landingZoneId) {
    LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(jobStatus)
            .statusCode(HttpStatus.SC_ACCEPTED)
            .submitted(Instant.now().toString())
            .resultURL("create-result/"));
    asyncJobResult.result(new StartLandingZoneCreation(landingZoneId, "mydefinition", "v1"));
    return asyncJobResult;
  }

  public static LandingZoneResourcesByPurpose createListLandingZoneResourcesByPurposeResult() {
    var listSubnets1 =
        List.of(
            LandingZoneResource.builder()
                .resourceName("fooSubnet11")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .build(),
            LandingZoneResource.builder()
                .resourceName("fooSubnet12")
                .resourceParentId("fooNetworkVNetId2")
                .region("fooRegion2")
                .build(),
            LandingZoneResource.builder()
                .resourceName("fooSubnet13")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .build());
    LandingZonePurpose purposeSubnets1 = SubnetResourcePurpose.AKS_NODE_POOL_SUBNET;

    var listSubnets2 =
        List.of(
            LandingZoneResource.builder()
                .resourceName("fooSubnet21")
                .resourceParentId("fooNetworkVNetId1")
                .region("fooRegion1")
                .build());
    LandingZonePurpose purposeSubnets2 = SubnetResourcePurpose.POSTGRESQL_SUBNET;

    var listResources3 =
        List.of(
            LandingZoneResource.builder()
                .resourceId("Id31")
                .resourceType("fooType31")
                .region("fooRegion1")
                .build(),
            LandingZoneResource.builder()
                .resourceId("Id32")
                .resourceType("fooType32")
                .region("fooRegion2")
                .build(),
            LandingZoneResource.builder()
                .resourceId("Id33")
                .resourceType("fooType33")
                .region("fooRegion1")
                .build());
    LandingZonePurpose purposeSubnets3 = ResourcePurpose.SHARED_RESOURCE;

    var listResources4 =
        List.of(
            LandingZoneResource.builder()
                .resourceId("Id41")
                .resourceType("fooType41")
                .region("fooRegion1")
                .build());
    LandingZonePurpose purposeSubnets4 = ResourcePurpose.WLZ_RESOURCE;

    return new LandingZoneResourcesByPurpose(
        Map.of(
            purposeSubnets4,
            listResources4,
            purposeSubnets3,
            listResources3,
            purposeSubnets1,
            listSubnets1,
            purposeSubnets2,
            listSubnets2));
  }
}
