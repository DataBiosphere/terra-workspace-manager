package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiResourceType;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

/**
 * Resource Family groups together controlled and referenced resource types of resources manged by
 * WSM. For example, CONTROLLED_GCS_BUCKET and REFERENCED_GCS_BUCKET are the controlled and
 * referenced resource types of the GCS_BUCKET resource family. Some resource families have only a
 * controlled type (e.g. AI_NOTEBOOK_INSTANCE) or only a referenced type (e.g. GIT_REPO) in which
 * case the other is null.
 */
public enum WsmResourceFamily {
  // ANY
  GIT_REPO("GIT_REPO", ApiResourceType.GIT_REPO, WsmResourceType.REFERENCED_ANY_GIT_REPO, null),
  TERRA_WORKSPACE(
      "TERRA_WORKSPACE",
      ApiResourceType.TERRA_WORKSPACE,
      WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE,
      null),
  DATA_REPO_SNAPSHOT(
      "DATA_REPO_SNAPSHOT",
      ApiResourceType.DATA_REPO_SNAPSHOT,
      WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT,
      null), // no controlled type for snapshots

  // GCP
  AI_NOTEBOOK_INSTANCE(
      "AI_NOTEBOOK_INSTANCE",
      ApiResourceType.AI_NOTEBOOK,
      null, // no reference type for notebooks,
      WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE),
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
  GCE_INSTANCE(
      "GCE_INSTANCE",
      ApiResourceType.GCE_INSTANCE,
      null, // no reference type for instances
      WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE),
  DATAPROC_CLUSTER(
      "DATAPROC_CLUSTER",
      ApiResourceType.DATAPROC_CLUSTER,
      null, // no reference type for dataproc clusters
      WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER),

  // AZURE
  AZURE_KUBERNETES_NAMESPACE(
      "AZURE_KUBERNETES_NAMESPACE",
      ApiResourceType.AZURE_KUBERNETES_NAMESPACE,
      null,
      WsmResourceType.CONTROLLED_AZURE_KUBERNETES_NAMESPACE),
  AZURE_MANAGED_IDENTITY(
      "AZURE_MANAGED_IDENTITY",
      ApiResourceType.AZURE_MANAGED_IDENTITY,
      null,
      WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY),
  AZURE_DATABASE(
      "AZURE_DATABASE",
      ApiResourceType.AZURE_DATABASE,
      null,
      WsmResourceType.CONTROLLED_AZURE_DATABASE),
  AZURE_DISK("AZURE_DISK", ApiResourceType.AZURE_DISK, null, WsmResourceType.CONTROLLED_AZURE_DISK),
  AZURE_VM("AZURE_VM", ApiResourceType.AZURE_VM, null, WsmResourceType.CONTROLLED_AZURE_VM),
  AZURE_STORAGE_CONTAINER(
      "AZURE_STORAGE_CONTAINER",
      ApiResourceType.AZURE_STORAGE_CONTAINER,
      null,
      WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER),
  AZURE_BATCH_POOL(
      "AZURE_BATCH_POOL",
      ApiResourceType.AZURE_BATCH_POOL,
      null,
      WsmResourceType.CONTROLLED_AZURE_BATCH_POOL),

  // AWS
  AWS_S3_STORAGE_FOLDER(
      "AWS_S3_STORAGE_FOLDER",
      ApiResourceType.AWS_S3_STORAGE_FOLDER,
      null, // TODO(TERRA-195) add support for referenced buckets
      WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER),
  AWS_SAGEMAKER_NOTEBOOK(
      "AWS_SAGEMAKER_NOTEBOOK",
      ApiResourceType.AWS_SAGEMAKER_NOTEBOOK,
      null, // no reference type for notebooks,
      WsmResourceType.CONTROLLED_AWS_SAGEMAKER_NOTEBOOK),

  // FLEXIBLE
  FLEXIBLE_RESOURCE(
      "FLEXIBLE_RESOURCE",
      ApiResourceType.FLEXIBLE_RESOURCE,
      null,
      WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);

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
