package bio.terra.workspace.app.controller;

import static bio.terra.workspace.service.resource.model.StewardshipType.fromApi;

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
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final Logger logger = LoggerFactory.getLogger(ResourceController.class);

  @Autowired
  public ResourceController(
      WsmResourceService resourceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.resourceService = resourceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<ApiResourceList> enumerateResources(
      UUID workspaceId,
      ApiResourceType resourceType,
      ApiStewardshipType stewardshipType,
      @Min(0) @Valid Integer offset,
      @Min(1) @Valid Integer limit) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    List<WsmResource> wsmResources =
        resourceService.enumerateResources(
            workspaceId,
            WsmResourceType.fromApi(resourceType),
            fromApi(stewardshipType),
            offset,
            limit,
            userRequest);

    List<ApiResourceDescription> apiResourceDescriptionList =
        wsmResources.stream().map(this::toApiModel).collect(Collectors.toList());

    var apiResourceList = new ApiResourceList().resources(apiResourceDescriptionList);
    return new ResponseEntity<>(apiResourceList, HttpStatus.OK);
  }

  // Convert a WsmResource into the API format for enumeration
  private ApiResourceDescription toApiModel(WsmResource wsmResource) {

    ApiResourceMetadata common = wsmResource.toApiMetadata();
    var union = new ApiResourceAttributesUnion();
    switch (wsmResource.getStewardshipType()) {
      case REFERENCED:
        ReferencedResource referencedResource = wsmResource.castToReferenceResource();
        switch (wsmResource.getResourceType()) {
          case BIG_QUERY_DATASET:
            {
              ReferencedBigQueryDatasetResource resource =
                  referencedResource.castToBigQueryDatasetResource();
              union.gcpBigQuery(resource.toApiAttributes());
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
                "Unknown resource type: " + wsmResource.getResourceType());
        }
        break; // referenced

      case CONTROLLED:
        ControlledResource controlledResource = wsmResource.castToControlledResource();
        switch (wsmResource.getResourceType()) {
          case GCS_BUCKET:
            {
              ControlledGcsBucketResource resource = controlledResource.castToGcsBucketResource();
              union.gcpGcsBucket(resource.toApiAttributes());
              break;
            }

          case BIG_QUERY_DATASET:
          case DATA_REPO_SNAPSHOT:
          default:
            throw new InternalLogicException(
                "Unimplemented controlled resource type: " + wsmResource.getResourceType());
        }
        break; // controlled

      default:
        throw new InternalLogicException("Unknown stewardship type");
    }

    return new ApiResourceDescription().metadata(common).resourceAttributes(union);
  }
}
