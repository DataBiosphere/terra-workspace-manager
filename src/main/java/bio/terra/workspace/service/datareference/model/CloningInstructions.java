package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;

/**
 * Enum describing how to treat a resource when its containing workspace is cloned.
 *
 * <p>TODO: cloning is currently not supported, and these values are never used. Add descriptions of
 * each behavior here when it exists.
 */
public enum CloningInstructions {
  COPY_NOTHING,
  COPY_DEFINITION,
  COPY_RESOURCE,
  COPY_REFERENCE;

  private static final BiMap<CloningInstructions, CloningInstructionsEnum> instructionMap =
      EnumBiMap.create(CloningInstructions.class, CloningInstructionsEnum.class);

  static {
    instructionMap.put(CloningInstructions.COPY_NOTHING, CloningInstructionsEnum.NOTHING);
    instructionMap.put(CloningInstructions.COPY_DEFINITION, CloningInstructionsEnum.DEFINITION);
    instructionMap.put(CloningInstructions.COPY_RESOURCE, CloningInstructionsEnum.RESOURCE);
    instructionMap.put(CloningInstructions.COPY_REFERENCE, CloningInstructionsEnum.REFERENCE);
  }

  public static CloningInstructions fromApiModel(CloningInstructionsEnum modelEnum) {
    return instructionMap.inverse().get(modelEnum);
  }

  public CloningInstructionsEnum toApiModel() {
    return instructionMap.get(this);
  }
}
