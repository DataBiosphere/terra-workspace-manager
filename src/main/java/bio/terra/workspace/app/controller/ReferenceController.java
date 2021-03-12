package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ReferenceApi;
<<<<<<< HEAD
import bio.terra.workspace.generated.model.ApiBigQueryDatasetReference;
import bio.terra.workspace.generated.model.ApiCreateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotReference;
import bio.terra.workspace.generated.model.ApiGcsBucketReference;
import bio.terra.workspace.generated.model.ApiUpdateDataReferenceRequestBody;
=======
import bio.terra.workspace.generated.model.BigQueryDatasetReference;
import bio.terra.workspace.generated.model.CreateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateGoogleBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshotReference;
import bio.terra.workspace.generated.model.GoogleBucketReference;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.reference.ReferenceBigQueryDatasetResource;
import bio.terra.workspace.service.resource.reference.ReferenceDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.reference.ReferenceGcsBucketResource;
import bio.terra.workspace.service.resource.reference.ReferenceResource;
import bio.terra.workspace.service.resource.reference.ReferenceResourceService;
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
public class ReferenceController implements ReferenceApi {

  private final ReferenceResourceService referenceResourceService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final Logger logger = LoggerFactory.getLogger(ReferenceController.class);

  @Autowired
  public ReferenceController(
      ReferenceResourceService referenceResourceService,
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
<<<<<<< HEAD
  public ResponseEntity<ApiGcsBucketReference> createBucketReference(
      UUID id, @Valid ApiCreateGcsBucketReferenceRequestBody body) {
=======
  public ResponseEntity<GoogleBucketReference> createBucketReference(
      UUID id, @Valid CreateGoogleBucketReferenceRequestBody body) {
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8

    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        ReferenceGcsBucketResource.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .bucketName(body.getBucket().getBucketName())
            .build();
    resource.validate();

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
<<<<<<< HEAD
    ApiGcsBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
=======
    GoogleBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiGcsBucketReference> getBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
    ApiGcsBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
=======
  public ResponseEntity<GoogleBucketReference> getBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
    GoogleBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiGcsBucketReference> getBucketReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
    ApiGcsBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
=======
  public ResponseEntity<GoogleBucketReference> getBucketReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
    GoogleBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBucketReference(
<<<<<<< HEAD
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
=======
      UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiBigQueryDatasetReference> createBigQueryDatasetReference(
      UUID id, @Valid ApiCreateBigQueryDatasetReferenceRequestBody body) {
=======
  public ResponseEntity<BigQueryDatasetReference> createBigQueryDatasetReference(
      UUID id, @Valid CreateBigQueryDatasetReferenceRequestBody body) {
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8

    // Construct a ReferenceBigQueryResource object from the API input
    var resource =
        ReferenceBigQueryDatasetResource.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .projectId(body.getDataset().getProjectId())
            .datasetName(body.getDataset().getDatasetId())
            .build();
    resource.validate();

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
<<<<<<< HEAD
    ApiBigQueryDatasetReference response =
=======
    BigQueryDatasetReference response =
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
        referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiBigQueryDatasetReference> getBigQueryDatasetReference(
=======
  public ResponseEntity<BigQueryDatasetReference> getBigQueryDatasetReference(
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
<<<<<<< HEAD
    ApiBigQueryDatasetReference response =
=======
    BigQueryDatasetReference response =
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
        referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiBigQueryDatasetReference> getBigQueryDatasetReferenceByName(
=======
  public ResponseEntity<BigQueryDatasetReference> getBigQueryDatasetReferenceByName(
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
<<<<<<< HEAD
    ApiBigQueryDatasetReference response =
=======
    BigQueryDatasetReference response =
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
        referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReference(
<<<<<<< HEAD
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
=======
      UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiDataRepoSnapshotReference> createDataRepoSnapshotReference(
      UUID id, @Valid ApiCreateDataRepoSnapshotReferenceRequestBody body) {

    var resource =
        ReferenceDataRepoSnapshotResource.builder()
            .workspaceId(id)
=======
  public ResponseEntity<DataRepoSnapshotReference> createDataRepoSnapshotReference(
      UUID workspaceId, @Valid CreateDataRepoSnapshotReferenceRequestBody body) {

    var resource =
        ReferenceDataRepoSnapshotResource.builder()
            .workspaceId(workspaceId)
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
            .name(body.getMetadata().getName())
            .description(body.getMetadata().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .instanceName(body.getSnapshot().getInstanceName())
            .snapshotId(body.getSnapshot().getSnapshot())
            .build();
    resource.validate();

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
<<<<<<< HEAD
    ApiDataRepoSnapshotReference response =
=======
    DataRepoSnapshotReference response =
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
        referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiDataRepoSnapshotReference> getDataRepoSnapshotReference(
=======
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReference(
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResource(id, referenceId, userReq);
<<<<<<< HEAD
    ApiDataRepoSnapshotReference response =
=======
    DataRepoSnapshotReference response =
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
        referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
<<<<<<< HEAD
  public ResponseEntity<ApiDataRepoSnapshotReference> getDataRepoSnapshotReferenceByName(
=======
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReferenceByName(
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResourceByName(id, name, userReq);
<<<<<<< HEAD
    ApiDataRepoSnapshotReference response =
=======
    DataRepoSnapshotReference response =
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
        referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReference(
<<<<<<< HEAD
      UUID id, UUID referenceId, ApiUpdateDataReferenceRequestBody body) {
=======
      UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
>>>>>>> c70503b89dcc5e97082fc766d9b40d11bd8ad0e8
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
