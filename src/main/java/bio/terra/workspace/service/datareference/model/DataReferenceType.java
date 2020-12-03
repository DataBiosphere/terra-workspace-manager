package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;
import com.google.common.collect.EnumHashBiMap;

/** Enum describing the type of object a data reference is pointing to. */
public enum DataReferenceType {
  /** A snapshot stored in Data Repo. Corresponds to a {@Code SnapshotReference} object. */
  DATA_REPO_SNAPSHOT;

  private static final BiMap<DataReferenceType, ReferenceTypeEnum> typeMap =
      EnumBiMap.create(DataReferenceType.class, ReferenceTypeEnum.class);

  private static final BiMap<DataReferenceType, String> sqlMap =
      EnumHashBiMap.create(DataReferenceType.class);

  static {
    typeMap.put(DataReferenceType.DATA_REPO_SNAPSHOT, ReferenceTypeEnum.DATA_REPO_SNAPSHOT);

    sqlMap.put(DATA_REPO_SNAPSHOT, "DATA_REPO_SNAPSHOT");
  }

  public static DataReferenceType fromApiModel(ReferenceTypeEnum modelEnum) {
    return typeMap.inverse().get(modelEnum);
  }

  public ReferenceTypeEnum toApiModel() {
    return typeMap.get(this);
  }

  /** Convert this to a String to be serialized to the DB. */
  public String toSql() {
    return sqlMap.get(this);
  }

  /** Deserialize a string from DB. */
  public static DataReferenceType fromSql(String s) {
    return sqlMap.inverse().get(s);
  }
}
