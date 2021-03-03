package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ReferenceApi;
import bio.terra.workspace.generated.model.BigQueryDatasetReference;
import bio.terra.workspace.generated.model.CreateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateGoogleBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshotReference;
import bio.terra.workspace.generated.model.GoogleBucketReference;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.reference.ReferenceBigQueryDatasetResource;
import bio.terra.workspace.service.resource.reference.ReferenceDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.reference.ReferenceGcsBucketResource;
import bio.terra.workspace.service.resource.reference.ReferenceResource;
import bio.terra.workspace.service.resource.reference.ReferenceResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.UUID;

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
  public ResponseEntity<GoogleBucketReference> createBucketReference(
      UUID id, @Valid CreateGoogleBucketReferenceRequestBody body) {

    // Construct a ReferenceGcsBucketResource object from the API input
    var resource =
        new ReferenceGcsBucketResource(
            id,
            UUID.randomUUID(), // mint a resource id for this bucket
            body.getMetadata().getName(),
            body.getMetadata().getDescription(),
            CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()),
            body.getBucket().getBucketName());
    resource.validate();

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    GoogleBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<GoogleBucketReference> getBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource = referenceResourceService.getReferenceResource(id, referenceId, userReq);
    GoogleBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<GoogleBucketReference> getBucketReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource = referenceResourceService.getReferenceResourceByName(id, name, userReq);
    GoogleBucketReference response = referenceResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBucketReference(
          UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Big Query Dataset -- //

  @Override
  public ResponseEntity<BigQueryDatasetReference> createBigQueryDatasetReference(
      UUID id, @Valid CreateBigQueryDatasetReferenceRequestBody body) {

    // Construct a ReferenceBigQueryResource object from the API input
    var resource =
        new ReferenceBigQueryDatasetResource(
            id,
            UUID.randomUUID(), // mint a resource id for this bucket
            body.getMetadata().getName(),
            body.getMetadata().getDescription(),
            CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()),
            body.getDataset().getProjectId(),
            body.getDataset().getDatasetId());
    resource.validate();

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    BigQueryDatasetReference response = referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<BigQueryDatasetReference> getBigQueryDatasetReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource = referenceResourceService.getReferenceResource(id, referenceId, userReq);
    BigQueryDatasetReference response = referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<BigQueryDatasetReference> getBigQueryDatasetReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource = referenceResourceService.getReferenceResourceByName(id, name, userReq);
    BigQueryDatasetReference response = referenceResource.castToBigQueryDatasetResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReference(
          UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // -- Data Repo Snapshot -- //

  @Override
  public ResponseEntity<DataRepoSnapshotReference> createDataRepoSnapshotReference(
      UUID id, @Valid CreateDataRepoSnapshotReferenceRequestBody body) {

    var resource =
        new ReferenceDataRepoSnapshotResource(
            id,
            UUID.randomUUID(), // mint a resource id for this bucket
            body.getMetadata().getName(),
            body.getMetadata().getDescription(),
            CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()),
            body.getSnapshot().getInstanceName(),
            body.getSnapshot().getSnapshot());
    resource.validate();

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    DataRepoSnapshotReference response = referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource = referenceResourceService.getReferenceResource(id, referenceId, userReq);
    DataRepoSnapshotReference response = referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReferenceByName(UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ReferenceResource referenceResource = referenceResourceService.getReferenceResourceByName(id, name, userReq);
    DataRepoSnapshotReference response = referenceResource.castToDataRepoSnapshotResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReference(
          UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    referenceResourceService.updateReferenceResource(id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
