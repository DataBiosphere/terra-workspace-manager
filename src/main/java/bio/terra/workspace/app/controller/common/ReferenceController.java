package bio.terra.workspace.app.controller.common;

import bio.terra.workspace.generated.model.DataReferenceRequestMetadata;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.WsmResourceType;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This contains code common across all reference controllers. It is not a controller itself.
 *
 * <p>Although we split endpoints across reference types for API clarity, many implementations are
 * shared. This class allows us to avoid duplicating code unecessarily.
 */
public abstract class ReferenceController {

  protected final Logger logger = LoggerFactory.getLogger(ReferenceController.class);

  protected DataReference createDataReference(
      UUID workspaceId,
      DataReferenceRequestMetadata requestMetadata,
      WsmResourceType referenceType,
      ReferenceObject referenceObject,
      AuthenticatedUserRequest userReq,
      DataReferenceValidationUtils dataReferenceValidation,
      DataReferenceService dataReferenceService) {
    logger.info(
        "Creating data reference in workspace {} for {} with metadata {}",
        workspaceId.toString(),
        userReq.getEmail(),
        requestMetadata.toString());
    DataReferenceValidationUtils.validateReferenceName(requestMetadata.getName());
    dataReferenceValidation.validateReferenceObject(referenceObject, referenceType, userReq);

    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(workspaceId)
            .name(requestMetadata.getName())
            .description(requestMetadata.getDescription())
            .referenceType(referenceType)
            .cloningInstructions(
                CloningInstructions.fromApiModel(requestMetadata.getCloningInstructions()))
            .referenceObject(referenceObject)
            .build();
    DataReference reference = dataReferenceService.createDataReference(referenceRequest, userReq);

    logger.info(
        "Created data reference {} in workspace {} for {}",
        reference.toString(),
        workspaceId.toString(),
        userReq.getEmail());
    return reference;
  }

  protected DataReference getReference(
      UUID workspaceId,
      UUID referenceId,
      AuthenticatedUserRequest userReq,
      DataReferenceService dataReferenceService) {
    logger.info(
        "Getting data reference by id {} in workspace {} for {}",
        referenceId.toString(),
        workspaceId.toString(),
        userReq.getEmail());
    DataReference ref = dataReferenceService.getDataReference(workspaceId, referenceId, userReq);
    logger.info(
        "Got data reference {} in workspace {} for {}",
        ref.toString(),
        workspaceId.toString(),
        userReq.getEmail());
    return ref;
  }

  protected DataReference getReferenceByName(
      UUID workspaceId,
      WsmResourceType type,
      String name,
      AuthenticatedUserRequest userReq,
      DataReferenceService dataReferenceService) {
    logger.info(
        "Getting data reference by name {} in workspace {} for {}",
        name,
        workspaceId.toString(),
        userReq.getEmail());
    DataReferenceValidationUtils.validateReferenceName(name);
    DataReference ref =
        dataReferenceService.getDataReferenceByName(workspaceId, type, name, userReq);
    logger.info(
        "Got data reference by name {} in workspace {} for {}",
        ref.toString(),
        workspaceId.toString(),
        userReq.getEmail());
    return ref;
  }

  protected void updateReference(
      UUID workspaceId,
      UUID referenceId,
      UpdateDataReferenceRequestBody body,
      AuthenticatedUserRequest userReq,
      DataReferenceService dataReferenceService) {
    if (body.getName() == null && body.getDescription() == null) {
      throw new InvalidDataReferenceException("Must specify name or description to update.");
    }

    if (body.getName() != null) {
      DataReferenceValidationUtils.validateReferenceName(body.getName());
    }

    logger.info(
        "Updating data reference by id {} in workspace {} for {} with body {}",
        referenceId.toString(),
        workspaceId.toString(),
        userReq.getEmail(),
        body.toString());

    dataReferenceService.updateDataReference(workspaceId, referenceId, body, userReq);
    logger.info(
        "Updating data reference by id {} in workspace {} for {} with body {}",
        referenceId.toString(),
        workspaceId.toString(),
        userReq.getEmail(),
        body.toString());
  }
}
