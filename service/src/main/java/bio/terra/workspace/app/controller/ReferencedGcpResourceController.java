package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ReferencedGcpResourceApi;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpBigQueryDataTableResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpBigQueryDatasetResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpDataRepoSnapshotResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpGcsObjectResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGitRepoResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

// TODO: GENERAL - add request validation

@Controller
public class ReferencedGcpResourceController implements ReferencedGcpResourceApi {

  private final ReferencedResourceService referenceResourceService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ResourceController resourceController;
  private final WorkspaceService workspaceService;
  private final HttpServletRequest request;
  private final Logger logger = LoggerFactory.getLogger(ReferencedGcpResourceController.class);

  @Autowired
  public ReferencedGcpResourceController(
      ReferencedResourceService referenceResourceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ResourceController resourceController,
      WorkspaceService workspaceService,
      HttpServletRequest request) {
    this.referenceResourceService = referenceResourceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.resourceController = resourceController;
    this.workspaceService = workspaceService;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  // -- GSC Bucket object -- //
  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> createGcsObjectReference(
      UUID workspaceId, @Valid ApiCreateGcpGcsObjectReferenceRequestBody body) {

    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferencedGcsObjectResource.builder()
            .workspaceId(workspaceId)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .bucketName(body.getFile().getBucketName())
            .fileName(body.getFile().getFileName())
            .build();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiGcpGcsObjectResource response = referenceResource.castToGcsObjectResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userRequest);
    ApiGcpGcsObjectResource response = referenceResource.castToGcsObjectResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userRequest);
    ApiGcpGcsObjectResource response = referenceResource.castToGcsObjectResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateGcsObjectReference(
      UUID workspaceId, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        workspaceId, referenceId, body.getName(), body.getDescription(), userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> updateBucketObjectReferenceResource(
      UUID workspaceId, UUID referenceId, ApiUpdateGcsBucketObjectReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String bucketName = body.getBucketName();
    String objectName = body.getObjectName();
    if (StringUtils.isEmpty(bucketName) && StringUtils.isEmpty(objectName)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, referenceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedGcsObjectResource.Builder updateBucketObjectResourceBuilder =
          referenceResourceService
              .getReferenceResource(workspaceId, referenceId, userRequest)
              .castToGcsObjectResource()
              .toBuilder();
      if (!StringUtils.isEmpty(bucketName)) {
        updateBucketObjectResourceBuilder.bucketName(bucketName);
      }
      if (!StringUtils.isEmpty(objectName)) {
        updateBucketObjectResourceBuilder.fileName(objectName);
      }
      referenceResourceService.updateReferenceResource(
          workspaceId,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateBucketObjectResourceBuilder.build(),
          userRequest);
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteGcsObjectReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.GCS_OBJECT);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- GCS Bucket -- //
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> createBucketReference(
      UUID id, @Valid ApiCreateGcpGcsBucketReferenceRequestBody body) {

    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferencedGcsBucketResource.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .bucketName(body.getBucket().getBucketName())
            .build();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiGcpGcsBucketResource response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userRequest);
    ApiGcpGcsBucketResource response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userRequest);
    ApiGcpGcsBucketResource response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBucketReference(
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> updateBucketReferenceResource(
      UUID id, UUID referenceId, ApiUpdateGcsBucketReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String bucketName = body.getBucketName();
    if (StringUtils.isEmpty(bucketName)) {
      referenceResourceService.updateReferenceResource(
          id, referenceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedGcsBucketResource.Builder updateBucketResourceBuilder =
          referenceResourceService
              .getReferenceResource(id, referenceId, userRequest)
              .castToGcsBucketResource()
              .toBuilder()
              .bucketName(bucketName);
      referenceResourceService.updateReferenceResource(
          id,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateBucketResourceBuilder.build(),
          userRequest);
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBucketReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.GCS_BUCKET);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- BigQuery DataTable -- //
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> createBigQueryDataTableReference(
      UUID id, @Valid ApiCreateGcpBigQueryDataTableReferenceRequestBody body) {
    var resource =
        ReferencedBigQueryDataTableResource.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .projectId(body.getDataTable().getProjectId())
            .datasetId(body.getDataTable().getDatasetId())
            .dataTableId(body.getDataTable().getDataTableId())
            .build();
    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiGcpBigQueryDataTableResource response =
        referenceResource.castToBigQueryDataTableResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userRequest);
    ApiGcpBigQueryDataTableResource response =
        referenceResource.castToBigQueryDataTableResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userRequest);
    ApiGcpBigQueryDataTableResource response =
        referenceResource.castToBigQueryDataTableResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDataTableReference(
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDataTableReferenceResource(
      UUID workspaceId, UUID referenceId, ApiUpdateBigQueryDataTableReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String updatedProjectId = body.getProjectId();
    String updatedDatasetId = body.getDatasetId();
    String updatedDataTableId = body.getDataTableId();
    if (StringUtils.isEmpty(updatedProjectId)
        && StringUtils.isEmpty(updatedDatasetId)
        && StringUtils.isEmpty(updatedDataTableId)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, referenceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedBigQueryDataTableResource.Builder updateBqTableResource =
          referenceResourceService
              .getReferenceResource(workspaceId, referenceId, userRequest)
              .castToBigQueryDataTableResource()
              .toBuilder();
      if (!StringUtils.isEmpty(updatedProjectId)) {
        updateBqTableResource.projectId(updatedProjectId);
      }
      if (!StringUtils.isEmpty(updatedDatasetId)) {
        updateBqTableResource.datasetId(updatedDatasetId);
      }
      if (!StringUtils.isEmpty(updatedDataTableId)) {
        updateBqTableResource.dataTableId(updatedDataTableId);
      }
      referenceResourceService.updateReferenceResource(
          workspaceId,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateBqTableResource.build(),
          userRequest);
    }

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDataTableReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> createBigQueryDatasetReference(
      UUID id, @Valid ApiCreateGcpBigQueryDatasetReferenceRequestBody body) {

    // Construct a ReferenceBigQueryResource object from the API input
    var resource =
        ReferencedBigQueryDatasetResource.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .projectId(body.getDataset().getProjectId())
            .datasetName(body.getDataset().getDatasetId())
            .build();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiGcpBigQueryDatasetResource response =
        referenceResource.castToBigQueryDatasetResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userRequest);
    ApiGcpBigQueryDatasetResource response =
        referenceResource.castToBigQueryDatasetResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userRequest);
    ApiGcpBigQueryDatasetResource response =
        referenceResource.castToBigQueryDatasetResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReference(
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReferenceResource(
      UUID workspaceId, UUID resourceId, ApiUpdateBigQueryDatasetReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String updatedDatasetId = body.getDatasetId();
    String updatedProjectId = body.getProjectId();
    if (StringUtils.isEmpty(updatedDatasetId) && StringUtils.isEmpty(updatedProjectId)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, resourceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedResource referencedResource =
          referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);
      ReferencedBigQueryDatasetResource bqDatasetResource =
          referencedResource.castToBigQueryDatasetResource();

      ReferencedBigQueryDatasetResource.Builder updatedBqDatasetResourceBuilder =
          bqDatasetResource.toBuilder();
      if (!StringUtils.isEmpty(updatedProjectId)) {
        updatedBqDatasetResourceBuilder.projectId(updatedProjectId);
      }
      if (!StringUtils.isEmpty(updatedDatasetId)) {
        updatedBqDatasetResourceBuilder.datasetName(updatedDatasetId);
      }
      referenceResourceService.updateReferenceResource(
          workspaceId,
          resourceId,
          body.getName(),
          body.getDescription(),
          updatedBqDatasetResourceBuilder.build(),
          userRequest);
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDatasetReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.BIG_QUERY_DATASET);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> createDataRepoSnapshotReference(
      UUID id, @Valid ApiCreateDataRepoSnapshotReferenceRequestBody body) {

    var resource =
        ReferencedDataRepoSnapshotResource.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .instanceName(body.getSnapshot().getInstanceName())
            .snapshotId(body.getSnapshot().getSnapshot())
            .build();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiDataRepoSnapshotResource response =
        referenceResource.castToDataRepoSnapshotResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userRequest);
    ApiDataRepoSnapshotResource response =
        referenceResource.castToDataRepoSnapshotResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userRequest);
    ApiDataRepoSnapshotResource response =
        referenceResource.castToDataRepoSnapshotResource().toApiResource();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReference(
      UUID workspaceId, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        workspaceId, referenceId, body.getName(), body.getDescription(), userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReferenceResource(
      UUID workspaceId, UUID resouceId, ApiUpdateDataRepoSnapshotReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String updatedSnapshot = body.getSnapshot();
    String updatedInstanceName = body.getInstanceName();
    if (StringUtils.isEmpty(updatedSnapshot) && StringUtils.isEmpty(updatedInstanceName)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, resouceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedDataRepoSnapshotResource.Builder updatedResourceBuilder =
          referenceResourceService
              .getReferenceResource(workspaceId, resouceId, userRequest)
              .castToDataRepoSnapshotResource()
              .toBuilder();
      if (!StringUtils.isEmpty(updatedSnapshot)) {
        updatedResourceBuilder.snapshotId(updatedSnapshot);
      }
      if (!StringUtils.isEmpty(updatedInstanceName)) {
        updatedResourceBuilder.instanceName(updatedInstanceName);
      }
      referenceResourceService.updateReferenceResource(
          workspaceId,
          resouceId,
          body.getName(),
          body.getDescription(),
          updatedResourceBuilder.build(),
          userRequest);
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteDataRepoSnapshotReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpGcsObjectResourceResult> cloneGcpGcsObjectReference(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (CloningInstructions.COPY_REFERENCE != effectiveCloningInstructions) {
      // Nothing to clone here
      final var emptyResult =
          new ApiCloneReferencedGcpGcsObjectResourceResult()
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceResourceId(sourceReferencedResource.getResourceId())
              .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }
    // Clone the reference
    final ReferencedResource clonedReferencedResource =
        referenceResourceService.cloneReferencedResource(
            sourceReferencedResource,
            body.getDestinationWorkspaceId(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpGcsObjectResourceResult()
            .resource(clonedReferencedResource.castToGcsObjectResource().toApiModel())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpGcsBucketResourceResult> cloneGcpGcsBucketReference(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (CloningInstructions.COPY_REFERENCE != effectiveCloningInstructions) {
      // Nothing to clone here
      final var emptyResult =
          new ApiCloneReferencedGcpGcsBucketResourceResult()
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceResourceId(sourceReferencedResource.getResourceId())
              .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }

    // Clone the reference
    final ReferencedResource clonedReferencedResource =
        referenceResourceService.cloneReferencedResource(
            sourceReferencedResource,
            body.getDestinationWorkspaceId(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpGcsBucketResourceResult()
            .resource(clonedReferencedResource.castToGcsBucketResource().toApiModel())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpBigQueryDataTableResourceResult>
      cloneGcpBigQueryDataTableReference(
          UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (CloningInstructions.COPY_REFERENCE != effectiveCloningInstructions) {
      // Nothing to clone here
      final var emptyResult =
          new ApiCloneReferencedGcpBigQueryDataTableResourceResult()
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceResourceId(sourceReferencedResource.getResourceId())
              .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }
    // Clone the reference
    final ReferencedResource clonedReferencedResource =
        referenceResourceService.cloneReferencedResource(
            sourceReferencedResource,
            body.getDestinationWorkspaceId(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpBigQueryDataTableResourceResult()
            .resource(clonedReferencedResource.castToBigQueryDataTableResource().toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpBigQueryDatasetResourceResult>
      cloneGcpBigQueryDatasetReference(
          UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (CloningInstructions.COPY_REFERENCE != effectiveCloningInstructions) {
      // Nothing to clone here
      final var emptyResult =
          new ApiCloneReferencedGcpBigQueryDatasetResourceResult()
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceResourceId(sourceReferencedResource.getResourceId())
              .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }

    // Clone the reference
    final ReferencedResource clonedReferencedResource =
        referenceResourceService.cloneReferencedResource(
            sourceReferencedResource,
            body.getDestinationWorkspaceId(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpBigQueryDatasetResourceResult()
            .resource(clonedReferencedResource.castToBigQueryDatasetResource().toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpDataRepoSnapshotResourceResult>
      cloneGcpDataRepoSnapshotReference(
          UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (CloningInstructions.COPY_REFERENCE != effectiveCloningInstructions) {
      // Nothing to clone here
      final var emptyResult =
          new ApiCloneReferencedGcpDataRepoSnapshotResourceResult()
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceResourceId(sourceReferencedResource.getResourceId())
              .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }

    // Clone the reference
    final ReferencedResource clonedReferencedResource =
        referenceResourceService.cloneReferencedResource(
            sourceReferencedResource,
            body.getDestinationWorkspaceId(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpDataRepoSnapshotResourceResult()
            .resource(clonedReferencedResource.castToDataRepoSnapshotResource().toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  // - Git Repo referenced resource - //
  @Override
  public ResponseEntity<ApiGitRepoResource> createGitRepoReference(
      UUID workspaceId, @Valid ApiCreateGitRepoReferenceRequestBody body) {
    // Construct a ReferenceGcsBucketResource object from the API input
    ReferencedGitRepoResource resource =
        ReferencedGitRepoResource.builder()
            .workspaceId(workspaceId)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .gitUrl(body.getGitrepo().getGitUrl())
            .build();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiGitRepoResource response = referenceResource.castToGitRepoResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReference(
      UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);
    ApiGitRepoResource response = referenceResource.castToGitRepoResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReferenceByName(
      UUID workspaceId, String resourceName) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(workspaceId, resourceName, userRequest);
    ApiGitRepoResource response = referenceResource.castToGitRepoResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateGitRepoReference(
      UUID workspaceId, UUID referenceId, ApiUpdateGitRepoReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String gitUrl = body.getGitUrl();
    if (StringUtils.isEmpty(gitUrl)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, referenceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedGitRepoResource.Builder updateGitRepoResource =
          referenceResourceService
              .getReferenceResource(workspaceId, referenceId, userRequest)
              .castToGitRepoResource()
              .toBuilder();
      if (!StringUtils.isEmpty(gitUrl)) {
        updateGitRepoResource.gitUrl(gitUrl);
      }
      referenceResourceService.updateReferenceResource(
          workspaceId,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateGitRepoResource.build(),
          userRequest);
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteGitRepoReference(
      UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.GIT_REPO);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  public ResponseEntity<ApiCloneReferencedGitRepoResourceResult> cloneGitRepoReference(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, userRequest);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (CloningInstructions.COPY_REFERENCE != effectiveCloningInstructions) {
      // Nothing to clone here
      final var emptyResult =
          new ApiCloneReferencedGitRepoResourceResult()
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceResourceId(sourceReferencedResource.getResourceId())
              .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
              .resource(null);
      return new ResponseEntity<>(emptyResult, HttpStatus.OK);
    }
    // Clone the reference
    final ReferencedResource clonedReferencedResource =
        referenceResourceService.cloneReferencedResource(
            sourceReferencedResource,
            body.getDestinationWorkspaceId(),
            body.getName(),
            body.getDescription(),
            userRequest);

    // Build the correct response type
    ApiCloneReferencedGitRepoResourceResult result =
        new ApiCloneReferencedGitRepoResourceResult()
            .resource(clonedReferencedResource.castToGitRepoResource().toApiModel())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }
}
