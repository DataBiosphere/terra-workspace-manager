package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;

/** Enum describing the type of object a data reference is pointing to. */
public enum DataReferenceType {
  /** A snapshot stored in Data Repo. Corresponds to a {@Code SnapshotReference} object. */
  DATA_REPO_SNAPSHOT;

  private static final BiMap<DataReferenceType, ReferenceTypeEnum> typeMap =
      EnumBiMap.create(DataReferenceType.class, ReferenceTypeEnum.class);

  static {
    typeMap.put(DataReferenceType.DATA_REPO_SNAPSHOT, ReferenceTypeEnum.DATA_REPO_SNAPSHOT);
  }

  public static DataReferenceType fromApiModel(ReferenceTypeEnum modelEnum) {
    return typeMap.inverse().get(modelEnum);
  }

  public ReferenceTypeEnum toApiModel() {
    return typeMap.get(this);
  }
}
