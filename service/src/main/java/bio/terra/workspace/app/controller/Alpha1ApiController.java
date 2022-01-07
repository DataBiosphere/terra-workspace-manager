package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.generated.controller.Alpha1Api;
import bio.terra.workspace.generated.model.ApiEnumerateJobsFilter;
import bio.terra.workspace.generated.model.ApiEnumerateJobsResult;
import bio.terra.workspace.generated.model.ApiEnumeratedJob;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.Alpha1Service;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.EnumeratedJob;
import bio.terra.workspace.service.workspace.model.EnumeratedJobs;
import bio.terra.workspace.service.workspace.model.JobStateFilter;
import com.google.common.annotations.VisibleForTesting;
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
      UUID workspaceId, Integer limit, String pageToken, ApiEnumerateJobsFilter body) {
    // Make sure Alpha1 is enabled
    features.alpha1EnabledCheck();

    // Prepare the inputs
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControllerValidationUtils.validatePaginationParams(0, limit);

    final WsmResourceType resourceType =
        WsmResourceType.fromApiOptional(
            Optional.ofNullable(body).map(ApiEnumerateJobsFilter::getResourceType).orElse(null));
    final StewardshipType stewardshipType =
        StewardshipType.fromApiOptional(
            Optional.ofNullable(body).map(ApiEnumerateJobsFilter::getStewardshipType).orElse(null));
    final String resourceName =
        Optional.ofNullable(body).map(ApiEnumerateJobsFilter::getResourceName).orElse(null);
    final JobStateFilter jobStateFilter =
        Optional.ofNullable(body)
            .map(apitype -> JobStateFilter.fromApi(apitype.getJobStateFilter()))
            .orElse(null);
    ValidationUtils.validateOptionalResourceName(resourceName);

    // Do the enumeration
    EnumeratedJobs enumeratedJobs =
        alpha1Service.enumerateJobs(
            workspaceId,
            userRequest,
            limit,
            pageToken,
            resourceType,
            stewardshipType,
            resourceName,
            jobStateFilter);

    // projectId
    String gcpProjectId =
        workspaceService.getAuthorizedGcpProject(workspaceId, userRequest).orElse(null);

    // Convert the result to API-speak
    ApiEnumerateJobsResult result =
        new ApiEnumerateJobsResult()
            .pageToken(enumeratedJobs.getPageToken())
            .totalResults(enumeratedJobs.getTotalResults());

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
              .resource(
                  optResource.map(r -> apiResourceFromWsmResource(r, gcpProjectId)).orElse(null));
      result.addResultsItem(apiJob);
    }

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  // Convert a WsmResource into the API format for enumeration
  @VisibleForTesting
  public ApiResourceUnion apiResourceFromWsmResource(WsmResource wsmResource, String gcpProjectId) {

    var union = new ApiResourceUnion();
    switch (wsmResource.getStewardshipType()) {
      case REFERENCED:
        ReferencedResource referencedResource = wsmResource.castToReferencedResource();
        switch (wsmResource.getResourceType()) {
          case BIG_QUERY_DATASET:
            {
              ReferencedBigQueryDatasetResource resource =
                  referencedResource.castToBigQueryDatasetResource();
              union.gcpBqDataset(resource.toApiResource());
              break;
            }
          case BIQ_QUERY_DATA_TABLE:
            {
              ReferencedBigQueryDataTableResource resource =
                  referencedResource.castToBigQueryDataTableResource();
              union.gcpBqDataTable(resource.toApiResource());
              break;
            }
          case DATA_REPO_SNAPSHOT:
            {
              ReferencedDataRepoSnapshotResource resource =
                  referencedResource.castToDataRepoSnapshotResource();
              union.gcpDataRepoSnapshot(resource.toApiResource());
              break;
            }

          case GCS_BUCKET:
            {
              ReferencedGcsBucketResource resource = referencedResource.castToGcsBucketResource();
              union.gcpGcsBucket(resource.toApiModel());
              break;
            }

          case GCS_OBJECT:
            {
              ReferencedGcsObjectResource resource = referencedResource.castToGcsObjectResource();
              union.gcpGcsObject(resource.toApiModel());
              break;
            }

          default:
            throw new InternalLogicException(
                "Unknown referenced resource type: " + wsmResource.getResourceType());
        }
        break; // referenced

      case CONTROLLED:
        ControlledResource controlledResource = wsmResource.castToControlledResource();
        switch (wsmResource.getResourceType()) {
          case AI_NOTEBOOK_INSTANCE:
            {
              ControlledAiNotebookInstanceResource resource =
                  controlledResource.castToAiNotebookInstanceResource();
              union.gcpAiNotebookInstance(resource.toApiResource(gcpProjectId));
              break;
            }
          case GCS_BUCKET:
            {
              ControlledGcsBucketResource resource = controlledResource.castToGcsBucketResource();
              union.gcpGcsBucket(resource.toApiResource());
              break;
            }

          case BIG_QUERY_DATASET:
            {
              ControlledBigQueryDatasetResource resource =
                  controlledResource.castToBigQueryDatasetResource();
              union.gcpBqDataset(resource.toApiResource(gcpProjectId));
              break;
            }
          case DATA_REPO_SNAPSHOT: // there is a use case for this, but low priority
            throw new InternalLogicException(
                "Unimplemented controlled resource type: " + wsmResource.getResourceType());

          default:
            throw new InternalLogicException(
                "Unknown controlled resource type: " + wsmResource.getResourceType());
        }
        break; // controlled

      default:
        throw new InternalLogicException("Unknown stewardship type");
    }

    return union;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
