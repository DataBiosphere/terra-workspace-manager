package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiResourceType;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum WsmResourceType {
  AI_NOTEBOOK_INSTANCE(
      "AI_NOTEBOOK_INSTANCE",
      ApiResourceType.AI_NOTEBOOK,
      null, // no reference type for notebooks,
      WsmResourceType2.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE),
  DATA_REPO_SNAPSHOT(
      "DATA_REPO_SNAPSHOT",
      ApiResourceType.DATA_REPO_SNAPSHOT,
      WsmResourceType2.REFERENCED_DATA_REPO_SNAPSHOT,
      null), // no controlled type for snapshots
  GCS_BUCKET(
      "GCS_BUCKET",
      ApiResourceType.GCS_BUCKET,
      WsmResourceType2.REFERENCED_GCP_GCS_BUCKET,
      WsmResourceType2.CONTROLLED_GCP_GCS_BUCKET),
  GCS_OBJECT(
      "GCS_OBJECT",
      ApiResourceType.GCS_OBJECT,
      WsmResourceType2.REFERENCED_GCP_GCS_OBJECT,
      null), // no controlled type for GCS objects
  BIG_QUERY_DATASET(
      "BIG_QUERY_DATASET",
      ApiResourceType.BIG_QUERY_DATASET,
      WsmResourceType2.REFERENCED_GCP_BIG_QUERY_DATASET,
      WsmResourceType2.CONTROLLED_GCP_BIG_QUERY_DATASET),
  BIG_QUERY_DATA_TABLE(
      "BIG_QUERY_DATA_TABLE",
      ApiResourceType.BIG_QUERY_DATA_TABLE,
      WsmResourceType2.REFERENCED_GCP_BIG_QUERY_DATA_TABLE,
      null), // no controlled type for BQ data table
  AZURE_IP(
      "AZURE_IP",
      ApiResourceType.AZURE_IP,
      null,
      WsmResourceType2.CONTROLLED_AZURE_IP),
  AZURE_DISK(
      "AZURE_DISK",
      ApiResourceType.AZURE_DISK,
      null,
      WsmResourceType2.CONTROLLED_AZURE_DISK),
  AZURE_NETWORK(
      "AZURE_NETWORK",
      ApiResourceType.AZURE_NETWORK,
      null,
      WsmResourceType2.CONTROLLED_AZURE_NETWORK),
  AZURE_VM(
      "AZURE_VM",
      ApiResourceType.AZURE_VM,
      null,
      WsmResourceType2.CONTROLLED_AZURE_VM),
  AZURE_STORAGE_ACCOUNT(
      "AZURE_STORAGE_ACCOUNT",
      ApiResourceType.AZURE_STORAGE_ACCOUNT,
      null,
      WsmResourceType2.CONTROLLED_AZURE_STORAGE_ACCOUNT);

  private final String dbString; // serialized form of the resource type
  private final ApiResourceType apiResourceType;
  private final WsmResourceType2 referenceType;
  private final WsmResourceType2 controlledType;

  WsmResourceType(
      String dbString,
      ApiResourceType apiResourceType,
      WsmResourceType2 referenceType,
      WsmResourceType2 controlledType) {
    this.dbString = dbString;
    this.apiResourceType = apiResourceType;
    this.referenceType = referenceType;
    this.controlledType = controlledType;
  }

  public static WsmResourceType fromSql(String dbString) {
    for (WsmResourceType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching cloud resource type for " + dbString);
  }

  /**
   * Convert from an optional api type to WsmResourceType. This method handles the case where the
   * API input is optional/can be null. If the input is null we return null and leave it to the
   * caller to raise any error.
   *
   * @param apiResourceType incoming resource type or null
   * @return valid resource type; null if input is null
   */
  public static @Nullable WsmResourceType fromApiOptional(
      @Nullable ApiResourceType apiResourceType) {
    if (apiResourceType == null) {
      return null;
    }
    for (WsmResourceType value : values()) {
      if (value.apiResourceType == apiResourceType) {
        return value;
      }
    }
    throw new ValidationException("Invalid resource type " + apiResourceType);
  }

  public WsmResourceType2 getReferenceType() {
    return referenceType;
  }

  public WsmResourceType2 getControlledType() {
    return controlledType;
  }

  public String toSql() {
    return dbString;
  }
}
