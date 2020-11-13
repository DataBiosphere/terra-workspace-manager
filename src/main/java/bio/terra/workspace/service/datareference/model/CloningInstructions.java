package bio.terra.workspace.service.datareference.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;

/**
 * Internal representation of data reference types.
 */
public enum CloningInstructions {
  COPY_NOTHING,
  COPY_DEFINITION,
  COPY_RESOURCE,
  COPY_REFERENCE;

  private static final BiMap<CloningInstructions, CloningInstructionsEnum> instructionMap =
      EnumBiMap.create(CloningInstructions.class, CloningInstructionsEnum.class);

  static {
    instructionMap.put(CloningInstructions.COPY_NOTHING, CloningInstructionsEnum.COPY_NOTHING);
    instructionMap.put(CloningInstructions.COPY_DEFINITION, CloningInstructionsEnum.COPY_DEFINITION);
    instructionMap.put(CloningInstructions.COPY_RESOURCE, CloningInstructionsEnum.COPY_RESOURCE);
    instructionMap.put(CloningInstructions.COPY_REFERENCE, CloningInstructionsEnum.COPY_REFERENCE);
  }

  public static CloningInstructions fromApiModel(CloningInstructionsEnum modelEnum) {
    return instructionMap.inverse().get(modelEnum);
  }

  public CloningInstructionsEnum toApiModel() {
    return instructionMap.get(this);
  }
}
