package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ResourceApi;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceDescription;
import bio.terra.workspace.generated.model.ApiResourceList;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

// TODO: GENERAL - add request validation

@Controller
public class ResourceController implements ResourceApi {

  private final WsmResourceService resourceService;
  private final WorkspaceService workspaceService;
  private final ReferencedResourceService referencedResourceService;

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final Logger logger = LoggerFactory.getLogger(ResourceController.class);

  @Autowired
  public ResourceController(
      WsmResourceService resourceService,
      WorkspaceService workspaceService,
      ReferencedResourceService referencedResourceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.resourceService = resourceService;
    this.workspaceService = workspaceService;
    this.referencedResourceService = referencedResourceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<ApiResourceList> enumerateResources(
      UUID workspaceId,
      @Min(0) @Valid Integer offset,
      @Min(1) @Valid Integer limit,
      @Valid ApiResourceType resource,
      @Valid ApiStewardshipType stewardship) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    List<WsmResource> wsmResources =
        resourceService.enumerateResources(
            workspaceId,
            WsmResourceType.fromApiOptional(resource),
            StewardshipType.fromApiOptional(stewardship),
            offset,
            limit,
            userRequest);
    // projectId
    String gcpProjectId = workspaceService.getGcpProject(workspaceId).orElse(null);

    List<ApiResourceDescription> apiResourceDescriptionList =
        wsmResources.stream()
            .map(r -> makeApiResourceDescription(r, gcpProjectId))
            .collect(Collectors.toList());

    var apiResourceList = new ApiResourceList().resources(apiResourceDescriptionList);
    return new ResponseEntity<>(apiResourceList, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Boolean> checkReferenceAccess(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    boolean isValid = referencedResourceService.checkAccess(workspaceId, resourceId, userRequest);
    return new ResponseEntity<>(isValid, HttpStatus.OK);
  }

  // Convert a WsmResource into the API format for enumeration
  @VisibleForTesting
  public ApiResourceDescription makeApiResourceDescription(
      WsmResource wsmResource, @Nullable String gcpProjectId) {

    ApiResourceMetadata common = wsmResource.toApiMetadata();
    var union = new ApiResourceAttributesUnion();
    switch (wsmResource.getStewardshipType()) {
      case REFERENCED:
        ReferencedResource referencedResource = wsmResource.castToReferencedResource();
        switch (wsmResource.getResourceType()) {
          case BIG_QUERY_DATASET:
            {
              ReferencedBigQueryDatasetResource resource =
                  referencedResource.castToBigQueryDatasetResource();
              union.gcpBqDataset(resource.toApiAttributes());
              break;
            }

          case DATA_REPO_SNAPSHOT:
            {
              ReferencedDataRepoSnapshotResource resource =
                  referencedResource.castToDataRepoSnapshotResource();
              union.gcpDataRepoSnapshot(resource.toApiAttributes());
              break;
            }

          case GCS_BUCKET:
            {
              ReferencedGcsBucketResource resource = referencedResource.castToGcsBucketResource();
              union.gcpGcsBucket(resource.toApiAttributes());
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
              union.gcpAiNotebookInstance(resource.toApiResource(gcpProjectId).getAttributes());
              break;
            }
          case GCS_BUCKET:
            {
              ControlledGcsBucketResource resource = controlledResource.castToGcsBucketResource();
              union.gcpGcsBucket(resource.toApiAttributes());
              break;
            }

          case BIG_QUERY_DATASET:
            {
              ControlledBigQueryDatasetResource resource =
                  controlledResource.castToBigQueryDatasetResource();
              union.gcpBqDataset(resource.toApiAttributes(gcpProjectId));
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

    return new ApiResourceDescription().metadata(common).resourceAttributes(union);
  }
}
