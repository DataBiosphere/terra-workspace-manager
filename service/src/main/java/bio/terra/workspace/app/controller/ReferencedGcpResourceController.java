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
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
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
  private final ResourceValidationUtils validationUtils;
  private final HttpServletRequest request;
  private final PetSaService petSaService;
  private final Logger logger = LoggerFactory.getLogger(ReferencedGcpResourceController.class);

  @Autowired
  public ReferencedGcpResourceController(
      ReferencedResourceService referenceResourceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ResourceValidationUtils validationUtils,
      HttpServletRequest request,
      PetSaService petSaService) {
    this.referenceResourceService = referenceResourceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.validationUtils = validationUtils;
    this.request = request;
    this.petSaService = petSaService;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  // -- GCS Bucket object -- //

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> createGcsObjectReference(
      UUID workspaceId, @Valid ApiCreateGcpGcsObjectReferenceRequestBody body) {
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.REFERENCED, body.getMetadata().getCloningInstructions());
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

    ReferencedGcsObjectResource referencedResource =
        referenceResourceService
            .createReferenceResource(resource, getAuthenticatedInfo())
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referencedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedGcsObjectResource referenceResource =
        referenceResourceService
            .getReferenceResource(id, referenceId, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedGcsObjectResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(id, name, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
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
      ReferencedGcsObjectResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceId, referenceId, userRequest)
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
      ReferencedGcsObjectResource.Builder updateBucketObjectResourceBuilder =
          referencedResource.toBuilder();
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
        workspaceId, resourceId, userRequest, WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- GCS Bucket -- //
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> createBucketReference(
      UUID id, @Valid ApiCreateGcpGcsBucketReferenceRequestBody body) {
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.REFERENCED, body.getMetadata().getCloningInstructions());
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

    ReferencedGcsBucketResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource, getAuthenticatedInfo())
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedGcsBucketResource referenceResource =
        referenceResourceService
            .getReferenceResource(id, referenceId, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedGcsBucketResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(id, name, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
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
      ReferencedGcsBucketResource referencedResource =
          referenceResourceService
              .getReferenceResource(id, referenceId, userRequest)
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
      ReferencedGcsBucketResource.Builder updateBucketResourceBuilder =
          referencedResource.toBuilder().bucketName(bucketName);
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
        workspaceId, resourceId, userRequest, WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- BigQuery DataTable -- //
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> createBigQueryDataTableReference(
      UUID id, @Valid ApiCreateGcpBigQueryDataTableReferenceRequestBody body) {
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.REFERENCED, body.getMetadata().getCloningInstructions());
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
    ReferencedBigQueryDataTableResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource, getAuthenticatedInfo())
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedBigQueryDataTableResource referenceResource =
        referenceResourceService
            .getReferenceResource(id, referenceId, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedBigQueryDataTableResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(id, name, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
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
      ReferencedBigQueryDataTableResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceId, referenceId, userRequest)
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      ReferencedBigQueryDataTableResource.Builder updateBqTableResource =
          referencedResource.toBuilder();
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
        workspaceId, resourceId, userRequest, WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> createBigQueryDatasetReference(
      UUID id, @Valid ApiCreateGcpBigQueryDatasetReferenceRequestBody body) {
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.REFERENCED, body.getMetadata().getCloningInstructions());
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

    ReferencedBigQueryDatasetResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource, getAuthenticatedInfo())
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedBigQueryDatasetResource referenceResource =
        referenceResourceService
            .getReferenceResource(id, referenceId, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedBigQueryDatasetResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(id, name, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
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
      ReferencedBigQueryDatasetResource referenceResource =
          referenceResourceService
              .getReferenceResource(workspaceId, resourceId, userRequest)
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
      ReferencedBigQueryDatasetResource.Builder updatedBqDatasetResourceBuilder =
          referenceResource.toBuilder();
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
        workspaceId, resourceId, userRequest, WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> createDataRepoSnapshotReference(
      UUID id, @Valid ApiCreateDataRepoSnapshotReferenceRequestBody body) {
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.REFERENCED, body.getMetadata().getCloningInstructions());

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

    ReferencedDataRepoSnapshotResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource, getAuthenticatedInfo())
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedDataRepoSnapshotResource referenceResource =
        referenceResourceService
            .getReferenceResource(id, referenceId, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedDataRepoSnapshotResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(id, name, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReferenceResource(
      UUID workspaceId, UUID resourceId, ApiUpdateDataRepoSnapshotReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String updatedSnapshot = body.getSnapshot();
    String updatedInstanceName = body.getInstanceName();
    if (StringUtils.isEmpty(updatedSnapshot) && StringUtils.isEmpty(updatedInstanceName)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, resourceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedDataRepoSnapshotResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceId, resourceId, userRequest)
              .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      ReferencedDataRepoSnapshotResource.Builder updatedResourceBuilder =
          referencedResource.toBuilder();
      if (!StringUtils.isEmpty(updatedSnapshot)) {
        updatedResourceBuilder.snapshotId(updatedSnapshot);
      }
      if (!StringUtils.isEmpty(updatedInstanceName)) {
        updatedResourceBuilder.instanceName(updatedInstanceName);
      }
      referenceResourceService.updateReferenceResource(
          workspaceId,
          resourceId,
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
        workspaceId, resourceId, userRequest, WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
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
    final ReferencedGcsObjectResource clonedReferencedResource =
        referenceResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                body.getName(),
                body.getDescription(),
                userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpGcsObjectResourceResult()
            .resource(clonedReferencedResource.toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpGcsBucketResourceResult> cloneGcpGcsBucketReference(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest petRequest = getCloningCredentials(workspaceId);
    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, petRequest);

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
    final ReferencedGcsBucketResource clonedReferencedResource =
        referenceResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                body.getName(),
                body.getDescription(),
                petRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpGcsBucketResourceResult()
            .resource(clonedReferencedResource.toApiResource())
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
    final ReferencedBigQueryDataTableResource clonedReferencedResource =
        referenceResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                body.getName(),
                body.getDescription(),
                userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpBigQueryDataTableResourceResult()
            .resource(clonedReferencedResource.toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpBigQueryDatasetResourceResult>
      cloneGcpBigQueryDatasetReference(
          UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest petRequest = getCloningCredentials(workspaceId);
    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceId, resourceId, petRequest);

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
    final ReferencedBigQueryDatasetResource clonedReferencedResource =
        referenceResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                body.getName(),
                body.getDescription(),
                petRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpBigQueryDatasetResourceResult()
            .resource(clonedReferencedResource.toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpDataRepoSnapshotResourceResult>
      cloneGcpDataRepoSnapshotReference(
          UUID workspaceId, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

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
    final ReferencedDataRepoSnapshotResource clonedReferencedResource =
        referenceResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                body.getName(),
                body.getDescription(),
                userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);

    // Build the correct response type
    final var result =
        new ApiCloneReferencedGcpDataRepoSnapshotResourceResult()
            .resource(clonedReferencedResource.toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  // - Git Repo referenced resource - //
  @Override
  public ResponseEntity<ApiGitRepoResource> createGitRepoReference(
      UUID workspaceId, @Valid ApiCreateGitRepoReferenceRequestBody body) {
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.REFERENCED, body.getMetadata().getCloningInstructions());
    // Construct a ReferenceGcsBucketResource object from the API input
    validationUtils.validateGitRepoUri(body.getGitrepo().getGitRepoUrl());
    ReferencedGitRepoResource resource =
        ReferencedGitRepoResource.builder()
            .workspaceId(workspaceId)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .gitRepoUrl(body.getGitrepo().getGitRepoUrl())
            .build();

    ReferencedGitRepoResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource, getAuthenticatedInfo())
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedGitRepoResource referenceResource =
        referenceResourceService
            .getReferenceResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReferenceByName(
      UUID workspaceId, String resourceName) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedGitRepoResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(workspaceId, resourceName, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateGitRepoReference(
      UUID workspaceId, UUID referenceId, ApiUpdateGitRepoReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String gitRepoUrl = body.getGitRepoUrl();
    if (StringUtils.isEmpty(gitRepoUrl)) {
      referenceResourceService.updateReferenceResource(
          workspaceId, referenceId, body.getName(), body.getDescription(), userRequest);
    } else {
      ReferencedGitRepoResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceId, referenceId, userRequest)
              .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);

      ReferencedGitRepoResource.Builder updateGitRepoResource = referencedResource.toBuilder();
      validationUtils.validateGitRepoUri(gitRepoUrl);
      updateGitRepoResource.gitRepoUrl(gitRepoUrl);

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
  public ResponseEntity<Void> deleteGitRepoReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceId, resourceId, userRequest, WsmResourceType.REFERENCED_ANY_GIT_REPO);
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
    final ReferencedGitRepoResource clonedReferencedResource =
        referenceResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                body.getName(),
                body.getDescription(),
                userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);

    // Build the correct response type
    ApiCloneReferencedGitRepoResourceResult result =
        new ApiCloneReferencedGitRepoResourceResult()
            .resource(clonedReferencedResource.toApiResource())
            .sourceWorkspaceId(sourceReferencedResource.getWorkspaceId())
            .sourceResourceId(sourceReferencedResource.getResourceId())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  /**
   * Get the Pet SA if available. Otherwise, we likely don't have a cloud context, so there isn't
   * one. In that case, return the original user request.
   */
  private AuthenticatedUserRequest getCloningCredentials(UUID workspaceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return petSaService.getWorkspacePetCredentials(workspaceId, userRequest).orElse(userRequest);
  }
}
