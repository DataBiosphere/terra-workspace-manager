package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.DataReferenceMetadata;
import bio.terra.workspace.service.datareference.model.DataReference;

/**
 * Utility functions for translating between interface objects and internal objects. Generally,
 * these should only be called by Controllers, as services and other internal layers should not know
 * about interface objects.
 */
public class ControllerTranslationUtils {

  public static DataReferenceMetadata metadataFromDataReference(DataReference dataReference) {
    return new DataReferenceMetadata()
        .referenceId(dataReference.referenceId())
        .workspaceId(dataReference.workspaceId())
        .name(dataReference.name())
        .referenceDescription(dataReference.referenceDescription())
        .cloningInstructions(dataReference.cloningInstructions().toApiModel());
  }
}
