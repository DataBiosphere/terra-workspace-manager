package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.generated.controller.Alpha1Api;
import bio.terra.workspace.generated.model.ApiEnumerateJobsResult;
import bio.terra.workspace.generated.model.ApiEnumeratedJob;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobStateFilter;
import bio.terra.workspace.generated.model.ApiLoadUrlListRequestBody;
import bio.terra.workspace.generated.model.ApiLoadUrlListResult;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.model.EnumeratedJob;
import bio.terra.workspace.service.job.model.EnumeratedJobs;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.JobStateFilter;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class Alpha1ApiController implements Alpha1Api {

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final WorkspaceService workspaceService;
  private final ControlledResourceService controlledResourceService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final FeatureConfiguration features;
  private final JobService jobService;
  private final JobApiUtils jobApiUtils;
  private final HttpServletRequest request;

  @Autowired
  public Alpha1ApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      WorkspaceService workspaceService,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      FeatureConfiguration features,
      JobService jobService,
      JobApiUtils jobApiUtils,
      HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.workspaceService = workspaceService;
    this.controlledResourceService = controlledResourceService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.features = features;
    this.jobService = jobService;
    this.jobApiUtils = jobApiUtils;
    this.request = request;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiLoadUrlListResult> loadUrlList(
      UUID workspaceId, UUID resourceId, ApiLoadUrlListRequestBody body) {
    features.alpha1EnabledCheck();
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource bucket =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceId, resourceId, SamControlledResourceActions.WRITE_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    workspaceService.validateWorkspaceState(workspaceId);
    String jobId =
        controlledResourceService.transferUrlListToGcsBucket(
            userRequest, workspaceId, bucket, body.getManifestFileUrl());

    return ResponseEntity.ok(fetchApiLoadSignedUrlListResult(jobId));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiLoadUrlListResult> fetchLoadUrlListResult(
      UUID workspaceId, UUID resourceId, String jobId) {
    features.alpha1EnabledCheck();
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest);
    return ResponseEntity.ok(fetchApiLoadSignedUrlListResult(jobId));
  }

  private ApiLoadUrlListResult fetchApiLoadSignedUrlListResult(String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiLoadUrlListResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiEnumerateJobsResult> enumerateJobs(
      UUID workspaceUuid,
      Integer limit,
      String pageToken,
      ApiResourceType resource,
      ApiStewardshipType stewardship,
      String name,
      ApiJobStateFilter jobState) {
    // Make sure Alpha1 is enabled
    features.alpha1EnabledCheck();

    // Prepare the inputs
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControllerValidationUtils.validatePaginationParams(0, limit);
    ResourceValidationUtils.validateOptionalResourceName(name);

    // Make sure the caller has read access to the workspace
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);

    // Do the enumeration
    EnumeratedJobs enumeratedJobs =
        jobService.enumerateJobs(
            workspaceUuid,
            limit,
            pageToken,
            WsmResourceFamily.fromApiOptional(resource),
            StewardshipType.fromApiOptional(stewardship),
            name,
            JobStateFilter.fromApi(jobState));

    // Convert the result to API-speak
    List<ApiEnumeratedJob> apiJobList = new ArrayList<>();
    for (EnumeratedJob enumeratedJob : enumeratedJobs.getResults()) {
      ApiJobReport jobReport =
          jobApiUtils.mapFlightStateToApiJobReport(enumeratedJob.getFlightState());
      Optional<WsmResource> optResource = enumeratedJob.getResource();

      Optional<UUID> destinationResourceIdMaybe =
          Optional.ofNullable(
              enumeratedJob
                  .getFlightState()
                  .getInputParameters()
                  .get(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class));

      ApiEnumeratedJob apiJob =
          new ApiEnumeratedJob()
              .jobReport(jobReport)
              .errorReport(
                  enumeratedJob
                      .getFlightState()
                      .getException()
                      .map(ErrorReportUtils::buildApiErrorReport)
                      .orElse(null))
              .jobDescription(enumeratedJob.getJobDescription())
              .operationType(enumeratedJob.getOperationType().toApiModel())
              .resourceType(optResource.map(r -> r.getResourceType().toApiModel()).orElse(null))
              .metadata(optResource.map(WsmResource::toApiMetadata).orElse(null))
              .resourceAttributes(optResource.map(WsmResource::toApiAttributesUnion).orElse(null))
              .destinationResourceId(destinationResourceIdMaybe.orElse(null));
      apiJobList.add(apiJob);
    }

    ApiEnumerateJobsResult result =
        new ApiEnumerateJobsResult()
            .pageToken(enumeratedJobs.getPageToken())
            .totalResults(enumeratedJobs.getTotalResults())
            .results(apiJobList);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
