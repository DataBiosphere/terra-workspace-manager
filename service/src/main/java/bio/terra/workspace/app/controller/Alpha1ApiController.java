package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.generated.controller.Alpha1Api;
import bio.terra.workspace.generated.model.ApiEnumerateJobsResult;
import bio.terra.workspace.generated.model.ApiEnumeratedJob;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobStateFilter;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.workspace.Alpha1Service;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.EnumeratedJob;
import bio.terra.workspace.service.workspace.model.EnumeratedJobs;
import bio.terra.workspace.service.workspace.model.JobStateFilter;
import com.google.common.annotations.VisibleForTesting;
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
      UUID workspaceId,
      Integer limit,
      String pageToken,
      ApiResourceType resource,
      ApiStewardshipType stewardship,
      String name,
      ApiJobStateFilter jobState) {
    // Make sure Alpha1 is enabled
    features.alpha1EnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, workspaceId, SamWorkspaceAction.READ);

    ControllerValidationUtils.validatePaginationParams(0, limit);
    ValidationUtils.validateOptionalResourceName(name);

    // Do the enumeration
    EnumeratedJobs enumeratedJobs =
        alpha1Service.enumerateJobs(
            workspaceId,
            userRequest,
            limit,
            pageToken,
            WsmResourceType.fromApiOptional(resource),
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
              .resource(optResource.map(this::apiResourceFromWsmResource).orElse(null));
      apiJobList.add(apiJob);
    }

    ApiEnumerateJobsResult result =
        new ApiEnumerateJobsResult()
            .pageToken(enumeratedJobs.getPageToken())
            .totalResults(enumeratedJobs.getTotalResults())
            .results(apiJobList);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  // Convert a WsmResource into the API format for enumeration
  @VisibleForTesting
  public ApiResourceUnion apiResourceFromWsmResource(WsmResource wsmResource) {

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
          case BIG_QUERY_DATA_TABLE:
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
              union.gcpAiNotebookInstance(resource.toApiResource());
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
              union.gcpBqDataset(resource.toApiResource());
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
        throw new InternalLogicException(
            "Unknown stewardship type: " + wsmResource.getStewardshipType());
    }

    return union;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
