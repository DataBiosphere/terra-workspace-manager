package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiResourceType;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum WsmResourceFamily {
  AI_NOTEBOOK_INSTANCE(
      "AI_NOTEBOOK_INSTANCE",
      ApiResourceType.AI_NOTEBOOK,
      null, // no reference type for notebooks,
      WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE),
  DATA_REPO_SNAPSHOT(
      "DATA_REPO_SNAPSHOT",
      ApiResourceType.DATA_REPO_SNAPSHOT,
      WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT,
      null), // no controlled type for snapshots
  GCS_BUCKET(
      "GCS_BUCKET",
      ApiResourceType.GCS_BUCKET,
      WsmResourceType.REFERENCED_GCP_GCS_BUCKET,
      WsmResourceType.CONTROLLED_GCP_GCS_BUCKET),
  GCS_OBJECT(
      "GCS_OBJECT",
      ApiResourceType.GCS_OBJECT,
      WsmResourceType.REFERENCED_GCP_GCS_OBJECT,
      null), // no controlled type for GCS objects
  BIG_QUERY_DATASET(
      "BIG_QUERY_DATASET",
      ApiResourceType.BIG_QUERY_DATASET,
      WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET,
      WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
  BIG_QUERY_DATA_TABLE(
      "BIG_QUERY_DATA_TABLE",
      ApiResourceType.BIG_QUERY_DATA_TABLE,
      WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE,
      null), // no controlled type for BQ data table
  AZURE_IP("AZURE_IP", ApiResourceType.AZURE_IP, null, WsmResourceType.CONTROLLED_AZURE_IP),
  AZURE_RELAY_NAMESPACE(
      "AZURE_RELAY_NAMESPACE",
      ApiResourceType.AZURE_RELAY_NAMESPACE,
      null,
      WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE),
  AZURE_DISK("AZURE_DISK", ApiResourceType.AZURE_DISK, null, WsmResourceType.CONTROLLED_AZURE_DISK),
  AZURE_NETWORK(
      "AZURE_NETWORK",
      ApiResourceType.AZURE_NETWORK,
      null,
      WsmResourceType.CONTROLLED_AZURE_NETWORK),
  AZURE_VM("AZURE_VM", ApiResourceType.AZURE_VM, null, WsmResourceType.CONTROLLED_AZURE_VM),
  AZURE_STORAGE_ACCOUNT(
      "AZURE_STORAGE_ACCOUNT",
      ApiResourceType.AZURE_STORAGE_ACCOUNT,
      null,
      WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT),
  AZURE_STORAGE_CONTAINER(
      "AZURE_STORAGE_CONTAINER",
      ApiResourceType.AZURE_STORAGE_CONTAINER,
      null,
      WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER),
  AWS_BUCKET("AWS_BUCKET", ApiResourceType.AWS_BUCKET, null, WsmResourceType.CONTROLLED_AWS_BUCKET),
  AWS_SAGEMAKER_NOTEBOOK(
      "AWS_SAGEMAKER_NOTEBOOK",
      ApiResourceType.AWS_SAGEMAKER_NOTEBOOK,
      null,
      WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK),
  GIT_REPO("GIT_REPO", ApiResourceType.GIT_REPO, WsmResourceType.REFERENCED_ANY_GIT_REPO, null),
  TERRA_WORKSPACE(
      "TERRA_WORKSPACE",
      ApiResourceType.TERRA_WORKSPACE,
      WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE,
      null);

  private final String dbString; // serialized form of the resource type
  private final ApiResourceType apiResourceType;
  private final WsmResourceType referenceType;
  private final WsmResourceType controlledType;

  WsmResourceFamily(
      String dbString,
      ApiResourceType apiResourceType,
      @Nullable WsmResourceType referenceType,
      @Nullable WsmResourceType controlledType) {
    this.dbString = dbString;
    this.apiResourceType = apiResourceType;
    this.referenceType = referenceType;
    this.controlledType = controlledType;
  }

  public static WsmResourceFamily fromSql(String dbString) {
    for (WsmResourceFamily value : values()) {
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
  public static @Nullable WsmResourceFamily fromApiOptional(
      @Nullable ApiResourceType apiResourceType) {
    if (apiResourceType == null) {
      return null;
    }
    for (WsmResourceFamily value : values()) {
      if (value.apiResourceType == apiResourceType) {
        return value;
      }
    }
    throw new ValidationException("Invalid resource type " + apiResourceType);
  }

  public Optional<WsmResourceType> getReferenceType() {
    return Optional.ofNullable(referenceType);
  }

  public Optional<WsmResourceType> getControlledType() {
    return Optional.ofNullable(controlledType);
  }

  public String toSql() {
    return dbString;
  }
}
