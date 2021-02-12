package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.controller.common.ReferenceController;
import bio.terra.workspace.common.utils.ControllerTranslationUtils;
import bio.terra.workspace.generated.controller.GoogleReferenceApi;
import bio.terra.workspace.generated.model.BigQueryDatasetReference;
import bio.terra.workspace.generated.model.CreateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateGoogleBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.GoogleBucketReference;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
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
public class GoogleReferenceController extends ReferenceController implements GoogleReferenceApi {

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
  public ResponseEntity<GoogleBucketReference> createBucketReference(
      UUID id, @Valid CreateGoogleBucketReferenceRequestBody body) {
    bio.terra.workspace.service.datareference.model.GoogleBucketReference referenceObject =
        bio.terra.workspace.service.datareference.model.GoogleBucketReference.create(
            body.getBucket().getBucketName());
    DataReference reference =
        createDataReference(
            id,
            body.getMetadata(),
            DataReferenceType.GOOGLE_BUCKET,
            referenceObject,
            getAuthenticatedInfo(),
            dataReferenceValidation,
            dataReferenceService);
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
            id,
            body.getMetadata(),
            DataReferenceType.BIG_QUERY_DATASET,
            referenceObject,
            getAuthenticatedInfo(),
            dataReferenceValidation,
            dataReferenceService);

    BigQueryDatasetReference response =
        new BigQueryDatasetReference()
            .metadata(ControllerTranslationUtils.metadataFromDataReference(reference))
            .dataset(
                ((bio.terra.workspace.service.datareference.model.BigQueryDatasetReference)
                        reference.referenceObject())
                    .toApiModel());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<GoogleBucketReference> getBucketReference(UUID id, UUID referenceId) {
    DataReference ref = getReference(id, referenceId, getAuthenticatedInfo(), dataReferenceService);
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
  public ResponseEntity<GoogleBucketReference> getBucketReferenceByName(UUID id, String name) {
    DataReference ref =
        getReferenceByName(
            id,
            DataReferenceType.GOOGLE_BUCKET,
            name,
            getAuthenticatedInfo(),
            dataReferenceService);
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
    DataReference ref = getReference(id, referenceId, getAuthenticatedInfo(), dataReferenceService);
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
    DataReference ref =
        getReferenceByName(
            id,
            DataReferenceType.BIG_QUERY_DATASET,
            name,
            getAuthenticatedInfo(),
            dataReferenceService);
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
  public ResponseEntity<Void> updateBucketReference(
      UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
    updateReference(id, referenceId, body, getAuthenticatedInfo(), dataReferenceService);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> updateBigQueryDatasetReference(
      UUID id, UUID referenceId, UpdateDataReferenceRequestBody body) {
    updateReference(id, referenceId, body, getAuthenticatedInfo(), dataReferenceService);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
