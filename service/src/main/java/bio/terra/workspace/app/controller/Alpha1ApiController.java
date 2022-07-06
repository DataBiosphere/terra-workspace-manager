package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.generated.controller.Alpha1Api;
import bio.terra.workspace.generated.model.ApiEnumerateJobsResult;
import bio.terra.workspace.generated.model.ApiEnumeratedJob;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobStateFilter;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.Alpha1Service;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.EnumeratedJob;
import bio.terra.workspace.service.workspace.model.EnumeratedJobs;
import bio.terra.workspace.service.workspace.model.JobStateFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class Alpha1ApiController implements Alpha1Api {
  private final Logger logger = LoggerFactory.getLogger(Alpha1ApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final Alpha1Service alpha1Service;
  private final WorkspaceService workspaceService;
  private final FeatureConfiguration features;
  private final JobService jobService;
  private final HttpServletRequest request;

  @Autowired
  public Alpha1ApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      Alpha1Service alpha1Service,
      WorkspaceService workspaceService,
      FeatureConfiguration features,
      JobService jobService,
      HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.alpha1Service = alpha1Service;
    this.workspaceService = workspaceService;
    this.features = features;
    this.jobService = jobService;
    this.request = request;
  }

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
        alpha1Service.enumerateJobs(
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
          jobService.mapFlightStateToApiJobReport(enumeratedJob.getFlightState());
      Optional<WsmResource> optResource = enumeratedJob.getResource();

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
              .resourceAttributes(optResource.map(WsmResource::toApiAttributesUnion).orElse(null));
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
