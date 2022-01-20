package bio.terra.workspace.service.resource;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.service.resource.controlled.*;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGitHubRepoResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum WsmResourceType {
  AI_NOTEBOOK_INSTANCE(
      CloudPlatform.GCP,
      "AI_NOTEBOOK_INSTANCE",
      ApiResourceType.AI_NOTEBOOK,
      /*referenceClass=*/ null,
      ControlledAiNotebookInstanceResource.class),
  DATA_REPO_SNAPSHOT(
      CloudPlatform.GCP,
      "DATA_REPO_SNAPSHOT",
      ApiResourceType.DATA_REPO_SNAPSHOT,
      ReferencedDataRepoSnapshotResource.class,
      /*controlledClass=*/ null),
  GCS_BUCKET(
      CloudPlatform.GCP,
      "GCS_BUCKET",
      ApiResourceType.GCS_BUCKET,
      ReferencedGcsBucketResource.class,
      ControlledGcsBucketResource.class),
  GCS_OBJECT(
      CloudPlatform.GCP,
      "GCS_OBJECT",
      ApiResourceType.GCS_OBJECT,
      ReferencedGcsObjectResource.class,
      /*controlledClass=*/ null),
  BIG_QUERY_DATASET(
      CloudPlatform.GCP,
      "BIG_QUERY_DATASET",
      ApiResourceType.BIG_QUERY_DATASET,
      ReferencedBigQueryDatasetResource.class,
      ControlledBigQueryDatasetResource.class),
  BIG_QUERY_DATA_TABLE(
      CloudPlatform.GCP,
      "BIG_QUERY_DATA_TABLE",
      ApiResourceType.BIG_QUERY_DATA_TABLE,
      ReferencedBigQueryDataTableResource.class,
      null),
  AZURE_IP(
      CloudPlatform.AZURE,
      "AZURE_IP",
      ApiResourceType.AZURE_IP,
      null,
      ControlledAzureIpResource.class),
  AZURE_DISK(
      CloudPlatform.AZURE,
      "AZURE_DISK",
      ApiResourceType.AZURE_DISK,
      null,
      ControlledAzureDiskResource.class),
  AZURE_NETWORK(
      CloudPlatform.AZURE,
      "AZURE_NETWORK",
      ApiResourceType.AZURE_NETWORK,
      null,
      ControlledAzureNetworkResource.class),
  AZURE_VM(
      CloudPlatform.AZURE,
      "AZURE_VM",
      ApiResourceType.AZURE_VM,
      null,
      ControlledAzureVmResource.class),
  AZURE_STORAGE_ACCOUNT(
      CloudPlatform.AZURE,
      "AZURE_STORAGE_ACCOUNT",
      ApiResourceType.AZURE_STORAGE_ACCOUNT,
      null,
      ControlledAzureStorageResource.class),
  GITHUB_REPO(
      null,
      "GITHUB_REPO",
      ApiResourceType.GITHUB_REPO,
      ReferencedGitHubRepoResource.class,
      null);

  private final CloudPlatform cloudPlatform;
  private final String dbString; // serialized form of the resource type
  private final ApiResourceType apiResourceType;
  private final Class<? extends ReferencedResource> referenceClass;
  private final Class<? extends ControlledResource> controlledClass;

  WsmResourceType(
      CloudPlatform cloudPlatform,
      String dbString,
      ApiResourceType apiResourceType,
      Class<? extends ReferencedResource> referenceClass,
      Class<? extends ControlledResource> controlledClass) {
    this.cloudPlatform = cloudPlatform;
    this.dbString = dbString;
    this.apiResourceType = apiResourceType;
    this.referenceClass = referenceClass;
    this.controlledClass = controlledClass;
  }

  public static WsmResourceType fromSql(String dbString) {
    for (WsmResourceType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching resource type for " + dbString);
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
      if (value.toApiModel() == apiResourceType) {
        return value;
      }
    }
    throw new ValidationException("Invalid resource type " + apiResourceType);
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public Class<? extends ReferencedResource> getReferenceClass() {
    return referenceClass;
  }

  public Class<? extends ControlledResource> getControlledClass() {
    return controlledClass;
  }

  public String toSql() {
    return dbString;
  }

  public ApiResourceType toApiModel() {
    return apiResourceType;
  }
}
