package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.controller.common.ReferenceController;
import bio.terra.workspace.common.utils.ControllerTranslationUtils;
import bio.terra.workspace.generated.controller.DataRepoReferenceApi;
import bio.terra.workspace.generated.model.CreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshotReference;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class DataRepoReferenceController extends ReferenceController
    implements DataRepoReferenceApi {

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

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> createDataRepoSnapshotReference(
      UUID id, @Valid CreateDataRepoSnapshotReferenceRequestBody body) {
    SnapshotReference referenceObject =
        SnapshotReference.create(
            body.getSnapshot().getInstanceName(), body.getSnapshot().getSnapshot());
    DataReference reference =
        createDataReference(
            id,
            body.getMetadata(),
            DataReferenceType.DATA_REPO_SNAPSHOT,
            referenceObject,
            getAuthenticatedInfo(),
            dataReferenceValidation,
            dataReferenceService);

    DataRepoSnapshotReference response =
        new DataRepoSnapshotReference()
            .metadata(ControllerTranslationUtils.metadataFromDataReference(reference))
            .snapshot(((SnapshotReference) reference.referenceObject()).toApiModel());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReference(
      UUID id, UUID referenceId) {
    DataReference ref = getReference(id, referenceId, getAuthenticatedInfo(), dataReferenceService);

    DataRepoSnapshotReference response =
        new DataRepoSnapshotReference()
            .snapshot(((SnapshotReference) ref.referenceObject()).toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataRepoSnapshotReference> getDataRepoSnapshotReferenceByName(
      UUID id, String name) {
    DataReference ref =
        getReferenceByName(
            id,
            DataReferenceType.DATA_REPO_SNAPSHOT,
            name,
            getAuthenticatedInfo(),
            dataReferenceService);
    DataRepoSnapshotReference response =
        new DataRepoSnapshotReference()
            .snapshot(((SnapshotReference) ref.referenceObject()).toApiModel())
            .metadata(ControllerTranslationUtils.metadataFromDataReference(ref));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataRepoSnapshotReference(
      @PathVariable("workspaceId") UUID id,
      @PathVariable("referenceId") UUID referenceId,
      @RequestBody UpdateDataReferenceRequestBody body) {
    updateReference(id, referenceId, body, getAuthenticatedInfo(), dataReferenceService);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
