package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.app.controller.shared.PropertiesUtils;
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
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.generated.model.ApiTerraWorkspaceResource;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.terra.workspace.ReferencedTerraWorkspaceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ReferencedGcpResourceController extends ControllerBase
    implements ReferencedGcpResourceApi {
  private final WorkspaceService workspaceService;
  private final WorkspaceDao workspaceDao;
  private final WsmResourceService wsmResourceService;
  private final ReferencedResourceService referencedResourceService;
  private final ResourceValidationUtils validationUtils;

  @Autowired
  public ReferencedGcpResourceController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      JobService jobService,
      JobApiUtils jobApiUtils,
      WorkspaceService workspaceService,
      WorkspaceDao workspaceDao,
      WsmResourceService wsmResourceService,
      ReferencedResourceService referencedResourceService,
      ResourceValidationUtils validationUtils) {
    super(authenticatedUserRequestFactory, request, samService, features, jobService, jobApiUtils);
    this.workspaceService = workspaceService;
    this.workspaceDao = workspaceDao;
    this.wsmResourceService = wsmResourceService;
    this.referencedResourceService = referencedResourceService;
    this.validationUtils = validationUtils;
  }

  // -- GCS Bucket object -- //

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> createGcsObjectReference(
      UUID workspaceUuid, @Valid ApiCreateGcpGcsObjectReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferencedGcsObjectResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    workspaceUuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .bucketName(body.getFile().getBucketName())
            .objectName(body.getFile().getFileName())
            .build();

    ReferencedGcsObjectResource referencedResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referencedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReference(
      UUID uuid, UUID referenceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsObjectResource referenceResource =
        referencedResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> getGcsObjectReferenceByName(
      UUID uuid, String name) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsObjectResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsObjectResource> updateBucketObjectReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateGcsBucketObjectReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource resource =
        referencedResourceService.validateReferencedResourceAndAction(
            userRequest, workspaceUuid, resourceId, SamWorkspaceAction.UPDATE_REFERENCE);
    wsmResourceService.updateResource(
        userRequest,
        resource,
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(StewardshipType.REFERENCED, body.getCloningInstructions()),
        new ReferencedGcsObjectAttributes(body.getBucketName(), body.getObjectName()));
    ReferencedGcsObjectResource updatedResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteGcsObjectReference(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_GCS_OBJECT, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- GCS Bucket -- //
  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> createBucketReference(
      UUID workspaceUuid, @Valid ApiCreateGcpGcsBucketReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    workspaceUuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .bucketName(body.getBucket().getBucketName())
            .build();

    ReferencedGcsBucketResource referenceResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReference(UUID uuid, UUID referenceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsBucketResource referenceResource =
        referencedResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucketReferenceByName(UUID uuid, String name) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedGcsBucketResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> updateBucketReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateGcsBucketReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.UPDATE_REFERENCE);
    ReferencedResource resource =
        referencedResourceService.validateReferencedResourceAndAction(
            userRequest, workspaceUuid, resourceId, SamWorkspaceAction.UPDATE_REFERENCE);
    wsmResourceService.updateResource(
        userRequest,
        resource,
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(StewardshipType.REFERENCED, body.getCloningInstructions()),
        new ReferencedGcsBucketAttributes(body.getBucketName()));
    final ReferencedGcsBucketResource updatedResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteBucketReference(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_GCS_BUCKET, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- BigQuery DataTable -- //
  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> createBigQueryDataTableReference(
      UUID workspaceUuid, @Valid ApiCreateGcpBigQueryDataTableReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    var resource =
        ReferencedBigQueryDataTableResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    workspaceUuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .projectId(body.getDataTable().getProjectId())
            .datasetId(body.getDataTable().getDatasetId())
            .dataTableId(body.getDataTable().getDataTableId())
            .build();
    ReferencedBigQueryDataTableResource referenceResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReference(
      UUID uuid, UUID referenceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDataTableResource referenceResource =
        referencedResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> getBigQueryDataTableReferenceByName(
      UUID uuid, String name) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDataTableResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDataTableResource> updateBigQueryDataTableReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateBigQueryDataTableReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource resource =
        referencedResourceService.validateReferencedResourceAndAction(
            userRequest, workspaceUuid, resourceId, SamWorkspaceAction.UPDATE_REFERENCE);
    wsmResourceService.updateResource(
        userRequest,
        resource,
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(StewardshipType.REFERENCED, body.getCloningInstructions()),
        new ReferencedBigQueryDataTableAttributes(
            body.getProjectId(), body.getDatasetId(), body.getDataTableId()));
    final ReferencedBigQueryDataTableResource updatedResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteBigQueryDataTableReference(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid,
        resourceId,
        WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE,
        userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> createBigQueryDatasetReference(
      UUID uuid, @Valid ApiCreateGcpBigQueryDatasetReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, uuid, SamWorkspaceAction.CREATE_REFERENCE);

    // Construct a ReferenceBigQueryResource object from the API input
    var resource =
        ReferencedBigQueryDatasetResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    uuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .projectId(body.getDataset().getProjectId())
            .datasetName(body.getDataset().getDatasetId())
            .build();

    ReferencedBigQueryDatasetResource referenceResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReference(
      UUID uuid, UUID referenceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDatasetResource referenceResource =
        referencedResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDatasetReferenceByName(
      UUID uuid, String name) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedBigQueryDatasetResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> updateBigQueryDatasetReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateBigQueryDatasetReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource resource =
        referencedResourceService.validateReferencedResourceAndAction(
            userRequest, workspaceUuid, resourceId, SamWorkspaceAction.UPDATE_REFERENCE);
    wsmResourceService.updateResource(
        userRequest,
        resource,
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(StewardshipType.REFERENCED, body.getCloningInstructions()),
        new ReferencedBigQueryDatasetAttributes(body.getProjectId(), body.getDatasetId()));
    final ReferencedBigQueryDatasetResource updatedResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteBigQueryDatasetReference(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Traced
  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> createDataRepoSnapshotReference(
      UUID uuid, @Valid ApiCreateDataRepoSnapshotReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, uuid, SamWorkspaceAction.CREATE_REFERENCE);

    var resource =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    uuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .instanceName(body.getSnapshot().getInstanceName())
            .snapshotId(body.getSnapshot().getSnapshot())
            .build();

    ReferencedDataRepoSnapshotResource referenceResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReference(
      UUID uuid, UUID referenceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedDataRepoSnapshotResource referenceResource =
        referencedResourceService
            .getReferenceResource(uuid, referenceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> getDataRepoSnapshotReferenceByName(
      UUID uuid, String name) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.READ);
    ReferencedDataRepoSnapshotResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(uuid, name)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDataRepoSnapshotResource> updateDataRepoSnapshotReferenceResource(
      UUID workspaceUuid, UUID resourceId, ApiUpdateDataRepoSnapshotReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource resource =
        referencedResourceService.validateReferencedResourceAndAction(
            userRequest, workspaceUuid, resourceId, SamWorkspaceAction.UPDATE_REFERENCE);
    wsmResourceService.updateResource(
        userRequest,
        resource,
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(StewardshipType.REFERENCED, body.getCloningInstructions()),
        new ReferencedDataRepoSnapshotAttributes(body.getInstanceName(), body.getSnapshot()));
    ReferencedDataRepoSnapshotResource updatedResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteDataRepoSnapshotReference(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCloneReferencedGcpGcsObjectResourceResult> cloneGcpGcsObjectReference(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateCloneReferenceAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId());
    // Do this after permission check. If both permission check and this fail, it's better to show
    // permission check error.
    if (body.getCloningInstructions() != null) {
      ResourceValidationUtils.validateCloningInstructions(
          StewardshipType.REFERENCED,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }

    final ReferencedResource sourceReferencedResource =
        referencedResourceService.getReferenceResource(workspaceUuid, resourceId);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (!effectiveCloningInstructions.isReferenceClone()) {
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
        referencedResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                // Pass in null for now, since we don't know destination folder id yet. Folder id
                // will be set in a step.
                /*destinationFolderId=*/ null,
                body.getName(),
                body.getDescription(),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                effectiveCloningInstructions,
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

  @Traced
  @Override
  public ResponseEntity<ApiCloneReferencedGcpGcsBucketResourceResult> cloneGcpGcsBucketReference(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateCloneReferenceAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId());
    // Do this after permission check. If both permission check and this fail, it's better to show
    // permission check error.
    if (body.getCloningInstructions() != null) {
      ResourceValidationUtils.validateCloningInstructions(
          StewardshipType.REFERENCED,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }

    final ReferencedResource sourceReferencedResource =
        referencedResourceService.getReferenceResource(workspaceUuid, resourceId);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (!effectiveCloningInstructions.isReferenceClone()) {
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
        referencedResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                // Pass in null for now, since we don't know destination folder id yet. Folder id
                // will be set in a step.
                /*destinationFolderId=*/ null,
                body.getName(),
                body.getDescription(),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                effectiveCloningInstructions,
                userRequest)
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

  @Traced
  @Override
  public ResponseEntity<ApiCloneReferencedGcpBigQueryDataTableResourceResult>
      cloneGcpBigQueryDataTableReference(
          UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateCloneReferenceAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId());
    // Do this after permission check. If both permission check and this fail, it's better to show
    // permission check error.
    if (body.getCloningInstructions() != null) {
      ResourceValidationUtils.validateCloningInstructions(
          StewardshipType.REFERENCED,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }

    final ReferencedResource sourceReferencedResource =
        referencedResourceService.getReferenceResource(workspaceUuid, resourceId);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (!effectiveCloningInstructions.isReferenceClone()) {
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
        referencedResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                // Pass in null for now, since we don't know destination folder id yet. Folder id
                // will be set in a step.
                /*destinationFolderId=*/ null,
                body.getName(),
                body.getDescription(),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                effectiveCloningInstructions,
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

  @Traced
  @Override
  public ResponseEntity<ApiCloneReferencedGcpBigQueryDatasetResourceResult>
      cloneGcpBigQueryDatasetReference(
          UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateCloneReferenceAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId());
    // Do this after permission check. If both permission check and this fail, it's better to show
    // permission check error.
    if (body.getCloningInstructions() != null) {
      ResourceValidationUtils.validateCloningInstructions(
          StewardshipType.REFERENCED,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }

    final ReferencedResource sourceReferencedResource =
        referencedResourceService.getReferenceResource(workspaceUuid, resourceId);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (!effectiveCloningInstructions.isReferenceClone()) {
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
        referencedResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                // Pass in null for now, since we don't know destination folder id yet. Folder id
                // will be set in a step.
                /*destinationFolderId=*/ null,
                body.getName(),
                body.getDescription(),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                effectiveCloningInstructions,
                userRequest)
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

  @Traced
  @Override
  public ResponseEntity<ApiCloneReferencedGcpDataRepoSnapshotResourceResult>
      cloneGcpDataRepoSnapshotReference(
          UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateCloneReferenceAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId());
    // Do this after permission check. If both permission check and this fail, it's better to show
    // permission check error.
    if (body.getCloningInstructions() != null) {
      ResourceValidationUtils.validateCloningInstructions(
          StewardshipType.REFERENCED,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }

    final ReferencedResource sourceReferencedResource =
        referencedResourceService.getReferenceResource(workspaceUuid, resourceId);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (!effectiveCloningInstructions.isReferenceClone()) {
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
        referencedResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                // Pass in null for now, since we don't know destination folder id yet. Folder id
                // will be set in a step.
                /*destinationFolderId=*/ null,
                body.getName(),
                body.getDescription(),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                effectiveCloningInstructions,
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
  @Traced
  @Override
  public ResponseEntity<ApiGitRepoResource> createGitRepoReference(
      UUID workspaceUuid, @Valid ApiCreateGitRepoReferenceRequestBody body) {
    // Construct a ReferencedGitRepoResource object from the API input
    validationUtils.validateGitRepoUri(body.getGitrepo().getGitRepoUrl());
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    ReferencedGitRepoResource resource =
        ReferencedGitRepoResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    workspaceUuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .gitRepoUrl(body.getGitrepo().getGitRepoUrl())
            .build();

    ReferencedGitRepoResource referenceResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReference(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedGitRepoResource referenceResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGitRepoResource> getGitRepoReferenceByName(
      UUID workspaceUuid, String resourceName) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedGitRepoResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(workspaceUuid, resourceName)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGitRepoResource> updateGitRepoReference(
      UUID workspaceUuid, UUID resourceId, ApiUpdateGitRepoReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ReferencedResource resource =
        referencedResourceService.validateReferencedResourceAndAction(
            userRequest, workspaceUuid, resourceId, SamWorkspaceAction.UPDATE_REFERENCE);
    String gitRepoUrl = body.getGitRepoUrl();
    if (gitRepoUrl != null) {
      validationUtils.validateGitRepoUri(gitRepoUrl);
    }
    wsmResourceService.updateResource(
        userRequest,
        resource,
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(StewardshipType.REFERENCED, body.getCloningInstructions()),
        new ReferencedGitRepoAttributes(gitRepoUrl));
    ReferencedGitRepoResource updatedResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_GIT_REPO);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteGitRepoReference(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_ANY_GIT_REPO, userRequest);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCloneReferencedGitRepoResourceResult> cloneGitRepoReference(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneReferencedResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // For cloning, we need to check that the caller has both read access to the source workspace
    // and write access to the destination workspace.
    workspaceService.validateCloneReferenceAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId());
    // Do this after permission check. If both permission check and this fail, it's better to show
    // permission check error.
    if (body.getCloningInstructions() != null) {
      ResourceValidationUtils.validateCloningInstructions(
          StewardshipType.REFERENCED,
          CloningInstructions.fromApiModel(body.getCloningInstructions()));
    }

    final ReferencedResource sourceReferencedResource =
        referencedResourceService.getReferenceResource(workspaceUuid, resourceId);

    final CloningInstructions effectiveCloningInstructions =
        Optional.ofNullable(body.getCloningInstructions())
            .map(CloningInstructions::fromApiModel)
            .orElse(sourceReferencedResource.getCloningInstructions());
    if (!effectiveCloningInstructions.isReferenceClone()) {
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
        referencedResourceService
            .cloneReferencedResource(
                sourceReferencedResource,
                body.getDestinationWorkspaceId(),
                UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
                // Pass in null for now, since we don't know destination folder id yet. Folder id
                // will be set in a step.
                /*destinationFolderId=*/ null,
                body.getName(),
                body.getDescription(),
                getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
                effectiveCloningInstructions,
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

  // - Terra workspace referenced resource - //
  @Traced
  @Override
  public ResponseEntity<ApiTerraWorkspaceResource> createTerraWorkspaceReference(
      UUID workspaceUuid, @Valid ApiCreateTerraWorkspaceReferenceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.CREATE_REFERENCE);
    UUID referencedWorkspaceId = body.getReferencedWorkspace().getReferencedWorkspaceId();

    // Will throw if the referenced workspace does not exist or workspace id is null.
    // Note that the user does not need read access in the destination workspace to reference it.
    workspaceDao.getWorkspace(referencedWorkspaceId);

    // Construct a ReferencedTerraWorkspaceResource object from the API input
    ReferencedTerraWorkspaceResource resource =
        ReferencedTerraWorkspaceResource.builder()
            .wsmResourceFields(
                getWsmResourceFields(
                    workspaceUuid,
                    body.getMetadata(),
                    getSamService().getUserEmailFromSamAndRethrowOnInterrupt(userRequest)))
            .referencedWorkspaceId(referencedWorkspaceId)
            .build();

    ReferencedTerraWorkspaceResource referenceResource =
        referencedResourceService
            .createReferenceResource(resource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiTerraWorkspaceResource> getTerraWorkspaceReference(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedTerraWorkspaceResource referenceResource =
        referencedResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiTerraWorkspaceResource> getTerraWorkspaceReferenceByName(
      UUID workspaceUuid, String resourceName) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    ReferencedTerraWorkspaceResource referenceResource =
        referencedResourceService
            .getReferenceResourceByName(workspaceUuid, resourceName)
            .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
    return new ResponseEntity<>(referenceResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteTerraWorkspaceReference(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.DELETE_REFERENCE);
    referencedResourceService.deleteReferenceResourceForResourceType(
        workspaceUuid, resourceId, WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE, userRequest);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  private static WsmResourceFields getWsmResourceFields(
      UUID workspaceUuid, ApiReferenceResourceCommonFields metadata, String createdByEmail) {
    return WsmResourceFields.builder()
        .workspaceUuid(workspaceUuid)
        .resourceId(UUID.randomUUID())
        .name(metadata.getName())
        .description(metadata.getDescription())
        .properties(PropertiesUtils.convertApiPropertyToMap(metadata.getProperties()))
        .cloningInstructions(CloningInstructions.fromApiModel(metadata.getCloningInstructions()))
        .createdByEmail(createdByEmail)
        .build();
  }
}
