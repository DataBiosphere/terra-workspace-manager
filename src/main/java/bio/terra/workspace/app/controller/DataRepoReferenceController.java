package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerTranslationUtils;
import bio.terra.workspace.generated.controller.DataRepoReferenceApi;
import bio.terra.workspace.generated.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshotReference;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
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

public class DataRepoReferenceController implements DataRepoReferenceApi {

  private final DataReferenceValidationUtils dataReferenceValidation;
  private final DataReferenceService dataReferenceService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public DataRepoReferenceController(
      DataReferenceValidationUtils dataReferenceValidation,
      DataReferenceService dataReferenceService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.dataReferenceValidation = dataReferenceValidation;
    this.dataReferenceService = dataReferenceService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private final Logger logger = LoggerFactory.getLogger(DataRepoReferenceController.class);

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> createDataRepoSnapshotReference(
      UUID id, @Valid CreateDataRepoSnapshotReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Creating Data Repo snapshot reference in workspace %s for %s with metadata %s",
            id.toString(), userReq.getEmail(), body.getMetadata().toString()));
    SnapshotReference referenceObject =
        SnapshotReference.create(
            body.getSnapshot().getInstanceName(), body.getSnapshot().getSnapshot());
    DataReferenceValidationUtils.validateReferenceName(body.getMetadata().getName());
    dataReferenceValidation.validateReferenceObject(
        referenceObject, DataReferenceType.DATA_REPO_SNAPSHOT, userReq);

    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(id)
            .name(body.getMetadata().getName())
            .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getMetadata().getCloningInstructions()))
            .referenceObject(referenceObject)
            .build();
    DataReference reference = dataReferenceService.createDataReference(referenceRequest, userReq);
    logger.info(
        String.format(
            "Created Data Repo snapshot reference %s in workspace %s for %s ",
            reference.toString(), id.toString(), userReq.getEmail()));

    DataRepoSnapshotReference response =
        new DataRepoSnapshotReference()
            .metadata(ControllerTranslationUtils.metadataFromDataReference(reference))
            .snapshot(((SnapshotReference) reference.referenceObject()).toApiModel());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReference(
      UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Getting data repo snapshot reference by id %s in workspace %s for %s",
            referenceId.toString(), id.toString(), userReq.getEmail()));
    DataReference ref = dataReferenceService.getDataReference(id, referenceId, userReq);
    logger.info(
        String.format(
            "Got data repo snapshot reference %s in workspace %s for %s",
            ref.toString(), id.toString(), userReq.getEmail()));
    DataRepoSnapshotReference response =
        new DataRepoSnapshotReference()
            .snapshot(((SnapshotReference) ref.referenceObject()).toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReferenceByName(
      UUID id, String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Getting data repo snapshot reference by name %s in workspace %s for %s",
            name, id.toString(), userReq.getEmail()));
    DataReferenceValidationUtils.validateReferenceName(name);
    DataReference ref =
        dataReferenceService.getDataReferenceByName(
            id, DataReferenceType.DATA_REPO_SNAPSHOT, name, userReq);
    logger.info(
        String.format(
            "Got data repo snapshot reference by name %s in workspace %s for %s",
            ref.toString(), id.toString(), userReq.getEmail()));
    DataRepoSnapshotReference response =
        new DataRepoSnapshotReference()
            .snapshot(((SnapshotReference) ref.referenceObject()).toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteDataRepoSnapshotReference(UUID id, UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        String.format(
            "Deleting data reference by id %s in workspace %s for %s",
            referenceId.toString(), id.toString(), userReq.getEmail()));
    dataReferenceService.deleteDataReference(id, referenceId, userReq);
    logger.info(
        String.format(
            "Deleted data reference by id %s in workspace %s for %s",
            referenceId.toString(), id.toString(), userReq.getEmail()));
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
