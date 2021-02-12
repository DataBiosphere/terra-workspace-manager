package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumHashBiMap;

/** Enum describing the type of object a data reference is pointing to. */
public enum DataReferenceType {
  /** A snapshot stored in Data Repo. Corresponds to a {@Code SnapshotReference} object. */
  DATA_REPO_SNAPSHOT,
  /** A GCS bucket. Corresponds to a {@Code GoogleBucketReference} object. */
  GOOGLE_BUCKET,
  /** A Google BigQuery dataset. Corresponds to a {@Code BigQueryReference} object. */
  BIG_QUERY_DATASET;

  private static final BiMap<DataReferenceType, String> sqlMap =
      EnumHashBiMap.create(DataReferenceType.class);

  static {
    sqlMap.put(DATA_REPO_SNAPSHOT, "DATA_REPO_SNAPSHOT");
    sqlMap.put(GOOGLE_BUCKET, "GOOGLE_BUCKET");
    sqlMap.put(BIG_QUERY_DATASET, "BIG_QUERY_DATASET");
  }

  public static DataReferenceType fromApiModel(ReferenceTypeEnum modelEnum) {
    return DataReferenceType.valueOf(modelEnum.name());
  }

  public ReferenceTypeEnum toApiModel() {
    return ReferenceTypeEnum.valueOf(this.name());
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
