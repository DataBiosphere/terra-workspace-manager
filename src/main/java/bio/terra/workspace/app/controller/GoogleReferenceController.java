package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerTranslationUtils;
import bio.terra.workspace.generated.controller.GoogleReferenceApi;
import bio.terra.workspace.generated.model.BigQueryDatasetReference;
import bio.terra.workspace.generated.model.CreateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateGoogleBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceRequestMetadata;
import bio.terra.workspace.generated.model.GoogleBucketReference;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class GoogleReferenceController implements GoogleReferenceApi {

  private final DataReferenceValidationUtils dataReferenceValidation;
  private final DataReferenceService dataReferenceService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public GoogleReferenceController(
      DataReferenceValidationUtils dataReferenceValidation,
      DataReferenceService dataReferenceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.dataReferenceValidation = dataReferenceValidation;
    this.dataReferenceService = dataReferenceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private final Logger logger = LoggerFactory.getLogger(GoogleReferenceController.class);

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<GoogleBucketReference> createGoogleBucketReference(
      UUID id, @Valid CreateGoogleBucketReferenceRequestBody body) {
    bio.terra.workspace.service.datareference.model.GoogleBucketReference referenceObject =
        bio.terra.workspace.service.datareference.model.GoogleBucketReference.create(
            body.getBucket().getBucketName());
    DataReference reference =
        createDataReference(
            id, body.getMetadata(), DataReferenceType.GOOGLE_BUCKET, referenceObject);
    GoogleBucketReference response =
        new GoogleBucketReference()
            .metadata(ControllerTranslationUtils.metadataFromDataReference(reference))
            .bucket(
                ((bio.terra.workspace.service.datareference.model.GoogleBucketReference)
                        reference.referenceObject())
                    .toApiModel());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<BigQueryDatasetReference> createBigQueryDatasetReference(
      UUID id, @Valid CreateBigQueryDatasetReferenceRequestBody body) {
    bio.terra.workspace.service.datareference.model.BigQueryDatasetReference referenceObject =
        bio.terra.workspace.service.datareference.model.BigQueryDatasetReference.create(
            body.getDataset().getProjectId(), body.getDataset().getDatasetId());
    DataReference reference =
        createDataReference(
            id, body.getMetadata(), DataReferenceType.BIG_QUERY_DATASET, referenceObject);
    BigQueryDatasetReference response =
        new BigQueryDatasetReference()
            .metadata(ControllerTranslationUtils.metadataFromDataReference(reference))
            .dataset(
                ((bio.terra.workspace.service.datareference.model.BigQueryDatasetReference)
                        reference.referenceObject())
                    .toApiModel());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private DataReference createDataReference(
      UUID workspaceId,
      DataReferenceRequestMetadata requestMetadata,
      DataReferenceType referenceType,
      ReferenceObject referenceObject) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Creating data reference in workspace %s for %s with metadata %s",
            workspaceId.toString(), userReq.getEmail(), requestMetadata.toString()));
    DataReferenceValidationUtils.validateReferenceName(requestMetadata.getName());
    dataReferenceValidation.validateReferenceObject(referenceObject, referenceType, userReq);

    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(workspaceId)
            .name(requestMetadata.getName())
            .referenceType(referenceType)
            .cloningInstructions(
                CloningInstructions.fromApiModel(requestMetadata.getCloningInstructions()))
            .referenceObject(referenceObject)
            .build();
    DataReference reference = dataReferenceService.createDataReference(referenceRequest, userReq);

    logger.info(
        String.format(
            "Created data reference %s in workspace %s for %s ",
            reference.toString(), workspaceId.toString(), userReq.getEmail()));
    return reference;
  }

  @Override
  public ResponseEntity<GoogleBucketReference> getGoogleBucketReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Getting Google bucket reference by id %s in workspace %s for %s",
            referenceId.toString(), id.toString(), userReq.getEmail()));
    DataReference ref = dataReferenceService.getDataReference(id, referenceId, userReq);
    logger.info(
        String.format(
            "Got Google bucket reference %s in workspace %s for %s",
            ref.toString(), id.toString(), userReq.getEmail()));
    GoogleBucketReference response =
        new GoogleBucketReference()
            .bucket(
                ((bio.terra.workspace.service.datareference.model.GoogleBucketReference)
                        ref.referenceObject())
                    .toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<GoogleBucketReference> getGoogleBucketReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Getting Google bucket reference by name %s in workspace %s for %s",
            name, id.toString(), userReq.getEmail()));
    DataReferenceValidationUtils.validateReferenceName(name);
    DataReference ref =
        dataReferenceService.getDataReferenceByName(
            id, DataReferenceType.GOOGLE_BUCKET, name, userReq);
    logger.info(
        String.format(
            "Got Google bucket reference by name %s in workspace %s for %s",
            ref.toString(), id.toString(), userReq.getEmail()));
    GoogleBucketReference response =
        new GoogleBucketReference()
            .bucket(
                ((bio.terra.workspace.service.datareference.model.GoogleBucketReference)
                        ref.referenceObject())
                    .toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<BigQueryDatasetReference> getBigQueryDatasetReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Getting BigQuery dataset reference by id %s in workspace %s for %s",
            referenceId.toString(), id.toString(), userReq.getEmail()));
    DataReference ref = dataReferenceService.getDataReference(id, referenceId, userReq);
    logger.info(
        String.format(
            "Got BigQuery dataset reference %s in workspace %s for %s",
            ref.toString(), id.toString(), userReq.getEmail()));
    BigQueryDatasetReference response =
        new BigQueryDatasetReference()
            .dataset(
                ((bio.terra.workspace.service.datareference.model.BigQueryDatasetReference)
                        ref.referenceObject())
                    .toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<BigQueryDatasetReference> getBigQueryDatasetReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Getting BigQuery dataset reference by name %s in workspace %s for %s",
            name, id.toString(), userReq.getEmail()));
    DataReferenceValidationUtils.validateReferenceName(name);
    DataReference ref =
        dataReferenceService.getDataReferenceByName(
            id, DataReferenceType.BIG_QUERY_DATASET, name, userReq);
    logger.info(
        String.format(
            "Got BigQuery dataset reference by name %s in workspace %s for %s",
            ref.toString(), id.toString(), userReq.getEmail()));
    BigQueryDatasetReference response =
        new BigQueryDatasetReference()
            .dataset(
                ((bio.terra.workspace.service.datareference.model.BigQueryDatasetReference)
                        ref.referenceObject())
                    .toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteGoogleBucketReference(UUID id, UUID referenceId) {
    deleteDataReference(id, referenceId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDatasetReference(UUID id, UUID referenceId) {
    deleteDataReference(id, referenceId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  private void deleteDataReference(UUID workspaceId, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Deleting data reference by id %s in workspace %s for %s",
            referenceId.toString(), workspaceId.toString(), userReq.getEmail()));
    dataReferenceService.deleteDataReference(workspaceId, referenceId, userReq);
    logger.info(
        String.format(
            "Deleted data reference by id %s in workspace %s for %s",
            referenceId.toString(), workspaceId.toString(), userReq.getEmail()));
  }
}
