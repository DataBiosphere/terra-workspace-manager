package bio.terra.workspace.common.fixtures;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import java.time.Instant;
import java.util.Collections;
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
      createJobResultWithSucceededState(String jobId, String landingZoneId) {
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
                new LandingZoneResource("resId", "resourceType", null, "westus"))));
    return asyncJobResult;
  }

  public static ApiCreateAzureLandingZoneRequestBody buildCreateAzureLandingZoneRequest(
      String jobId) {
    return new ApiCreateAzureLandingZoneRequestBody()
        .azureContext(
            new ApiAzureContext()
                .resourceGroupId("resourceGroup")
                .subscriptionId("subscriptionId")
                .tenantId("tenantId"))
        .jobControl(new ApiJobControl().id(jobId))
        .definition("azureLandingZoneDefinition")
        .version("v1");
  }

  public static ApiCreateAzureLandingZoneRequestBody
      buildCreateAzureLandingZoneRequestWithoutDefinition(String jobId) {
    return new ApiCreateAzureLandingZoneRequestBody()
        .azureContext(
            new ApiAzureContext()
                .resourceGroupId("resourceGroup")
                .subscriptionId("subscriptionId")
                .tenantId("tenantId"))
        .jobControl(new ApiJobControl().id(jobId))
        .version("v1");
  }
}
