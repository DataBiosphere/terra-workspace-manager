package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ReferencedGcpResourceApi;
import bio.terra.workspace.generated.model.ApiBigQueryDatasetReference;
import bio.terra.workspace.generated.model.ApiCreateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotReference;
import bio.terra.workspace.generated.model.ApiGcsBucketReference;
import bio.terra.workspace.generated.model.ApiUpdateDataReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
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
  private final HttpServletRequest request;
  private final Logger logger = LoggerFactory.getLogger(ReferencedGcpResourceController.class);

  @Autowired
  public ReferencedGcpResourceController(
      ReferencedResourceService referenceResourceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.referenceResourceService = referenceResourceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  // -- GCS Bucket -- //

  @Override
  public ResponseEntity<ApiGcsBucketReference> createBucketReference(
      UUID id, @Valid ApiCreateGcsBucketReferenceRequestBody body) {

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
    resource.validate();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiGcsBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcsBucketReference> getBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
    ApiGcsBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcsBucketReference> getBucketReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
    ApiGcsBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBucketReference(
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBucketReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResource(workspaceId, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Override
  public ResponseEntity<ApiBigQueryDatasetReference> createBigQueryDatasetReference(
      UUID id, @Valid ApiCreateBigQueryDatasetReferenceRequestBody body) {

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
    resource.validate();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiBigQueryDatasetReference response =
        referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiBigQueryDatasetReference> getBigQueryDatasetReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
    ApiBigQueryDatasetReference response =
        referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiBigQueryDatasetReference> getBigQueryDatasetReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
    ApiBigQueryDatasetReference response =
        referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReference(
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDatasetReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResource(workspaceId, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Override
  public ResponseEntity<ApiDataRepoSnapshotReference> createDataRepoSnapshotReference(
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
    resource.validate();

    ReferencedResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    ApiDataRepoSnapshotReference response =
        referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotReference> getDataRepoSnapshotReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
    ApiDataRepoSnapshotReference response =
        referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDataRepoSnapshotReference> getDataRepoSnapshotReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferencedResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
    ApiDataRepoSnapshotReference response =
        referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReference(
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteDataRepoSnapshotReference(UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    referenceResourceService.deleteReferenceResource(workspaceId, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
