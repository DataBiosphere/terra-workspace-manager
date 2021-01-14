package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;
import com.google.common.collect.EnumHashBiMap;

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

  private static final BiMap<CloningInstructions, String> sqlMap =
      EnumHashBiMap.create(CloningInstructions.class);

  static {
    instructionMap.put(CloningInstructions.COPY_NOTHING, CloningInstructionsEnum.NOTHING);
    instructionMap.put(CloningInstructions.COPY_DEFINITION, CloningInstructionsEnum.DEFINITION);
    instructionMap.put(CloningInstructions.COPY_RESOURCE, CloningInstructionsEnum.RESOURCE);
    instructionMap.put(CloningInstructions.COPY_REFERENCE, CloningInstructionsEnum.REFERENCE);

    sqlMap.put(COPY_NOTHING, "COPY_NOTHING");
    sqlMap.put(COPY_DEFINITION, "COPY_DEFINITION");
    sqlMap.put(COPY_RESOURCE, "COPY_RESOURCE");
    sqlMap.put(COPY_REFERENCE, "COPY_REFERENCE");
  }

  public static CloningInstructions fromApiModel(CloningInstructionsEnum modelEnum) {
    return instructionMap.inverse().get(modelEnum);
  }

  public CloningInstructionsEnum toApiModel() {
    return instructionMap.get(this);
  }

  /** Convert this to a String to be serialized to the DB. */
  public String toSql() {
    return sqlMap.get(this);
  }

  /** Deserialize a string from DB. */
  public static CloningInstructions fromSql(String s) {
    return sqlMap.inverse().get(s);
  }
}
