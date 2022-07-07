package bio.terra.workspace.app.controller;

import bio.terra.workspace.db.WorkspaceDao;
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
import bio.terra.workspace.generated.model.ApiCreateTerraWorkspaceReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiTerraWorkspaceResource;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.terra.workspace.ReferencedTerraWorkspaceResource;
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

@Controller
public class ReferencedGcpResourceController implements ReferencedGcpResourceApi {

  private final ReferencedResourceService referenceResourceService;
  private final WorkspaceDao workspaceDao;
  private final WorkspaceService workspaceService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ResourceValidationUtils validationUtils;
  private final HttpServletRequest request;
  private final Logger logger = LoggerFactory.getLogger(ReferencedGcpResourceController.class);

  @Autowired
  public ReferencedGcpResourceController(
      ReferencedResourceService referenceResourceService,
      WorkspaceDao workspaceDao, WorkspaceService workspaceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ResourceValidationUtils validationUtils,
      HttpServletRequest request) {
    this.referenceResourceService = referenceResourceService;
    this.workspaceDao = workspaceDao;
    this.workspaceService = workspaceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.validationUtils = validationUtils;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  // -- GCS Bucket object -- //

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> createGcsObjectReference(
      UUID workspaceUuid, @Valid ApiCreateGcpGcsObjectReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferencedGcsObjectResource.builder()
            .workspaceId(workspaceUuid)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .bucketName(body.getFile().getBucketName())
            .fileName(body.getFile().getFileName())
            .build();

    ReferencedGcsObjectResource referencedResource =
        referenceResourceService
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referencedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReference(
      UUID uuid, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsObjectResource referenceResource =
        referenceResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReferenceByName(
      UUID uuid, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsObjectResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBucketObjectReferenceResource(
      UUID workspaceUuid, UUID referenceId, ApiUpdateGcsBucketObjectReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    String bucketName = body.getBucketName();
    String objectName = body.getObjectName();
    CloningInstructions cloningInstructions =
        CloningInstructions.fromApiModel(body.getCloningInstructions());
    if (StringUtils.isEmpty(bucketName) && StringUtils.isEmpty(objectName)) {
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          null,
          cloningInstructions);
    } else {
      ReferencedGcsObjectResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, referenceId)
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
      ReferencedGcsObjectResource.Builder updateBucketObjectResourceBuilder =
          referencedResource.toBuilder();
      if (!StringUtils.isEmpty(bucketName)) {
        updateBucketObjectResourceBuilder.bucketName(bucketName);
      }
      if (!StringUtils.isEmpty(objectName)) {
        updateBucketObjectResourceBuilder.fileName(objectName);
      }
      if (null != cloningInstructions) {
        updateBucketObjectResourceBuilder.cloningInstructions(cloningInstructions);
      }
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateBucketObjectResourceBuilder.build(),
          null); // included in resource arg
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteGcsObjectReference(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- GCS Bucket -- //
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> createBucketReference(
      UUID workspaceUuid, @Valid ApiCreateGcpGcsBucketReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferencedGcsBucketResource.builder()
            .workspaceId(workspaceUuid)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .bucketName(body.getBucket().getBucketName())
            .build();

    ReferencedGcsBucketResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReference(UUID uuid, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsBucketResource referenceResource =
        referenceResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReferenceByName(UUID uuid, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsBucketResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBucketReferenceResource(
      UUID workspaceUuid, UUID referenceId, ApiUpdateGcsBucketReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    String bucketName = body.getBucketName();
    CloningInstructions cloningInstructions =
        CloningInstructions.fromApiModel(body.getCloningInstructions());
    if (StringUtils.isEmpty(bucketName)) {
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          null,
          cloningInstructions);
    } else {
      ReferencedGcsBucketResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, referenceId)
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
      ReferencedGcsBucketResource.Builder updateBucketResourceBuilder =
          referencedResource.toBuilder().bucketName(bucketName);
      if (null != cloningInstructions) {
        // only overwrite if non-null
        updateBucketResourceBuilder.cloningInstructions(cloningInstructions);
      }
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateBucketResourceBuilder.build(),
          null); // passed in via resource argument
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBucketReference(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- BigQuery DataTable -- //
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> createBigQueryDataTableReference(
      UUID workspaceUuid, @Valid ApiCreateGcpBigQueryDataTableReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    var resource =
        ReferencedBigQueryDataTableResource.builder()
            .workspaceId(workspaceUuid)
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
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReference(
      UUID uuid, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDataTableResource referenceResource =
        referenceResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReferenceByName(
      UUID uuid, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDataTableResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDataTableReferenceResource(
      UUID workspaceUuid, UUID referenceId, ApiUpdateBigQueryDataTableReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    String updatedProjectId = body.getProjectId();
    String updatedDatasetId = body.getDatasetId();
    String updatedDataTableId = body.getDataTableId();
    CloningInstructions cloningInstructions =
        CloningInstructions.fromApiModel(body.getCloningInstructions());
    if (StringUtils.isEmpty(updatedProjectId)
        && StringUtils.isEmpty(updatedDatasetId)
        && StringUtils.isEmpty(updatedDataTableId)) {
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          null,
          cloningInstructions);
    } else {
      ReferencedBigQueryDataTableResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, referenceId)
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
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateBqTableResource.build(),
          cloningInstructions);
    }

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDataTableReference(
      UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> createBigQueryDatasetReference(
      UUID uuid, @Valid ApiCreateGcpBigQueryDatasetReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, uuid, SamWorkspaceAction.CREATE_REFERENCE);

    // Construct a ReferenceBigQueryResource object from the API input
    var resource =
        ReferencedBigQueryDatasetResource.builder()
            .workspaceId(uuid)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .projectId(body.getDataset().getProjectId())
            .datasetName(body.getDataset().getDatasetId())
            .build();

    ReferencedBigQueryDatasetResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReference(
      UUID uuid, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDatasetResource referenceResource =
        referenceResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReferenceByName(
      UUID uuid, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDatasetResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateBigQueryDatasetReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    String updatedDatasetId = body.getDatasetId();
    String updatedProjectId = body.getProjectId();
    CloningInstructions cloningInstructions =
        CloningInstructions.fromApiModel(body.getCloningInstructions());
    if (StringUtils.isEmpty(updatedDatasetId) && StringUtils.isEmpty(updatedProjectId)) {
      // identity of the resource is the same
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          resourceId,
          body.getName(),
          body.getDescription(),
          null,
          cloningInstructions);
    } else {
      // build new one from scratch
      ReferencedBigQueryDatasetResource referenceResource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resourceId)
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
          workspaceUuid,
          resourceId,
          body.getName(),
          body.getDescription(),
          updatedBqDatasetResourceBuilder.build(),
          cloningInstructions);
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDatasetReference(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> createDataRepoSnapshotReference(
      UUID uuid, @Valid ApiCreateDataRepoSnapshotReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, uuid, SamWorkspaceAction.CREATE_REFERENCE);

    var resource =
        ReferencedDataRepoSnapshotResource.builder()
            .workspaceId(uuid)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .instanceName(body.getSnapshot().getInstanceName())
            .snapshotId(body.getSnapshot().getSnapshot())
            .build();

    ReferencedDataRepoSnapshotResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReference(
      UUID uuid, UUID referenceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedDataRepoSnapshotResource referenceResource =
        referenceResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReferenceByName(
      UUID uuid, String name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedDataRepoSnapshotResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateDataRepoSnapshotReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    String updatedSnapshot = body.getSnapshot();
    String updatedInstanceName = body.getInstanceName();
    if (StringUtils.isEmpty(updatedSnapshot) && StringUtils.isEmpty(updatedInstanceName)) {
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          resourceId,
          body.getName(),
          body.getDescription(),
          null,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    } else {
      ReferencedDataRepoSnapshotResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resourceId)
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
          workspaceUuid,
          resourceId,
          body.getName(),
          body.getDescription(),
          updatedResourceBuilder.build(),
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteDataRepoSnapshotReference(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiCloneReferencedGcpGcsObjectResourceResult> cloneGcpGcsObjectReference(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndAction(
        userRequest, body.getDestinationWorkspaceId(), SamWorkspaceAction.CREATE_REFERENCE);

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

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
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                body.getName(),
                body.getDescription())
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
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndAction(
        userRequest, body.getDestinationWorkspaceId(), SamWorkspaceAction.CREATE_REFERENCE);
    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

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
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                body.getName(),
                body.getDescription())
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
          UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndAction(
        userRequest, body.getDestinationWorkspaceId(), SamWorkspaceAction.CREATE_REFERENCE);

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

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
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                body.getName(),
                body.getDescription())
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
          UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndAction(
        userRequest, body.getDestinationWorkspaceId(), SamWorkspaceAction.CREATE_REFERENCE);
    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

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
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                body.getName(),
                body.getDescription())
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
          UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndAction(
        userRequest, body.getDestinationWorkspaceId(), SamWorkspaceAction.CREATE_REFERENCE);

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

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
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                body.getName(),
                body.getDescription())
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
      UUID workspaceUuid, @Valid ApiCreateGitRepoReferenceRequestBody body) {
    // Construct a ReferencedGitRepoResource object from the API input
    validationUtils.validateGitRepoUri(body.getGitrepo().getGitRepoUrl());
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    ReferencedGitRepoResource resource =
        ReferencedGitRepoResource.builder()
            .workspaceId(workspaceUuid)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .gitRepoUrl(body.getGitrepo().getGitRepoUrl())
            .build();

    ReferencedGitRepoResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReference(
      UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedGitRepoResource referenceResource =
        referenceResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReferenceByName(
      UUID workspaceUuid, String resourceName) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedGitRepoResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(workspaceUuid, resourceName)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateGitRepoReference(
      UUID workspaceUuid, UUID referenceId, ApiUpdateGitRepoReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    String gitRepoUrl = body.getGitRepoUrl();
    if (StringUtils.isEmpty(gitRepoUrl)) {
      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          null,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    } else {
      ReferencedGitRepoResource referencedResource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, referenceId)
              .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);

      ReferencedGitRepoResource.Builder updateGitRepoResource = referencedResource.toBuilder();
      validationUtils.validateGitRepoUri(gitRepoUrl);
      updateGitRepoResource.gitRepoUrl(gitRepoUrl);

      referenceResourceService.updateReferenceResource(
          workspaceUuid,
          referenceId,
          body.getName(),
          body.getDescription(),
          updateGitRepoResource.build(),
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteGitRepoReference(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  public ResponseEntity<ApiCloneReferencedGitRepoResourceResult> cloneGitRepoReference(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndAction(
        userRequest, body.getDestinationWorkspaceId(), SamWorkspaceAction.CREATE_REFERENCE);

    final ReferencedResource sourceReferencedResource =
        referenceResourceService.getReferenceResource(workspaceUuid, resourceId);

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
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                body.getName(),
                body.getDescription())
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

  // - Terra workspace referenced resource - //
  @Override
  public ResponseEntity<ApiTerraWorkspaceResource> createTerraWorkspaceReference(
      UUID workspaceUuid, @Valid ApiCreateTerraWorkspaceReferenceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    UUID referencedWorkspaceId = body.getReferencedWorkspace().getReferencedWorkspaceId();

    // Will throw if the referenced workspace does not exist or workspace id is null.
    // Note that the user does not need read access in the destination workspace to reference it.
    workspaceDao.getWorkspace(referencedWorkspaceId);

    // Construct a ReferencedTerraWorkspaceResource object from the API input
    ReferencedTerraWorkspaceResource resource =
        ReferencedTerraWorkspaceResource.builder()
            .workspaceId(workspaceUuid)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .referencedWorkspaceId(referencedWorkspaceId)
            .build();

    ReferencedTerraWorkspaceResource referenceResource =
        referenceResourceService
            .createReferenceResource(resource)
            .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiTerraWorkspaceResource> getTerraWorkspaceReference(
      UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedTerraWorkspaceResource referenceResource =
        referenceResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiTerraWorkspaceResource> getTerraWorkspaceReferenceByName(
      UUID workspaceUuid, String resourceName) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedTerraWorkspaceResource referenceResource =
        referenceResourceService
            .getReferenceResourceByName(workspaceUuid, resourceName)
            .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteTerraWorkspaceReference(UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referenceResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
