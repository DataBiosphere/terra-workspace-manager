package bio.terra.workspace.common.fixtures;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.service.landingzone.azure.model.DeletedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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

  public static LandingZoneJobService.AsyncJobResult<DeletedLandingZone>
      createDeleteJobResultWithSucceededState(String jobId, UUID landingZoneId) {
    LandingZoneJobService.AsyncJobResult<DeletedLandingZone> asyncJobResult =
        new LandingZoneJobService.AsyncJobResult<>();
    asyncJobResult.jobReport(
        new JobReport()
            .id(jobId)
            .description("description")
            .status(JobReport.StatusEnum.SUCCEEDED)
            .statusCode(HttpStatus.SC_OK)
            .submitted(Instant.now().toString())
            .resultURL("delete-result/"));

    asyncJobResult.result(new DeletedLandingZone(landingZoneId, List.of("resource/id")));
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
}
