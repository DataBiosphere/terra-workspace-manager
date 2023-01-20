package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook.ControlledAwsSageMakerNotebookHandler;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket.ControlledAwsBucketHandler;
import bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket.ControlledAwsBucketResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmHandler;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotHandler;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoHandler;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetHandler;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableHandler;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketHandler;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectHandler;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.terra.workspace.ReferencedTerraWorkspaceHandler;
import bio.terra.workspace.service.resource.referenced.terra.workspace.ReferencedTerraWorkspaceResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.function.Supplier;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

/**
 * Each resource implementation gets a specific type. This enumeration describes the attributes of
 * the resource type.
 */
public enum WsmResourceType {
  CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE(
      CloudPlatform.GCP,
      StewardshipType.CONTROLLED,
      "CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE",
      ApiResourceType.AI_NOTEBOOK,
      ControlledAiNotebookInstanceResource.class,
      ControlledAiNotebookHandler::getHandler),

  REFERENCED_ANY_DATA_REPO_SNAPSHOT(
      CloudPlatform.ANY,
      StewardshipType.REFERENCED,
      "REFERENCED_ANY_DATA_REPO_SNAPSHOT",
      ApiResourceType.DATA_REPO_SNAPSHOT,
      ReferencedDataRepoSnapshotResource.class,
      ReferencedDataRepoSnapshotHandler::getHandler),

  REFERENCED_GCP_GCS_BUCKET(
      CloudPlatform.GCP,
      StewardshipType.REFERENCED,
      "REFERENCED_GCP_GCS_BUCKET",
      ApiResourceType.GCS_BUCKET,
      ReferencedGcsBucketResource.class,
      ReferencedGcsBucketHandler::getHandler),

  CONTROLLED_GCP_GCS_BUCKET(
      CloudPlatform.GCP,
      StewardshipType.CONTROLLED,
      "CONTROLLED_GCP_GCS_BUCKET",
      ApiResourceType.GCS_BUCKET,
      ControlledGcsBucketResource.class,
      ControlledGcsBucketHandler::getHandler),

  REFERENCED_GCP_GCS_OBJECT(
      CloudPlatform.GCP,
      StewardshipType.REFERENCED,
      "REFERENCED_GCP_GCS_OBJECT",
      ApiResourceType.GCS_OBJECT,
      ReferencedGcsObjectResource.class,
      ReferencedGcsObjectHandler::getHandler),

  REFERENCED_GCP_BIG_QUERY_DATASET(
      CloudPlatform.GCP,
      StewardshipType.REFERENCED,
      "REFERENCED_GCP_BIG_QUERY_DATASET",
      ApiResourceType.BIG_QUERY_DATASET,
      ReferencedBigQueryDatasetResource.class,
      ReferencedBigQueryDatasetHandler::getHandler),

  CONTROLLED_GCP_BIG_QUERY_DATASET(
      CloudPlatform.GCP,
      StewardshipType.CONTROLLED,
      "CONTROLLED_GCP_BIG_QUERY_DATASET",
      ApiResourceType.BIG_QUERY_DATASET,
      ControlledBigQueryDatasetResource.class,
      ControlledBigQueryDatasetHandler::getHandler),

  REFERENCED_GCP_BIG_QUERY_DATA_TABLE(
      CloudPlatform.GCP,
      StewardshipType.REFERENCED,
      "REFERENCED_GCP_BIG_QUERY_DATA_TABLE",
      ApiResourceType.BIG_QUERY_DATA_TABLE,
      ReferencedBigQueryDataTableResource.class,
      ReferencedBigQueryDataTableHandler::getHandler),
  REFERENCED_ANY_GIT_REPO(
      CloudPlatform.ANY,
      StewardshipType.REFERENCED,
      "REFERENCED_ANY_GIT_REPO",
      ApiResourceType.GIT_REPO,
      ReferencedGitRepoResource.class,
      ReferencedGitRepoHandler::getHandler),
  REFERENCED_ANY_TERRA_WORKSPACE(
      CloudPlatform.ANY,
      StewardshipType.REFERENCED,
      "REFERENCED_ANY_TERRA_WORKSPACE",
      ApiResourceType.TERRA_WORKSPACE,
      ReferencedTerraWorkspaceResource.class,
      ReferencedTerraWorkspaceHandler::getHandler),
  CONTROLLED_AZURE_IP(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_IP",
      ApiResourceType.AZURE_IP,
      ControlledAzureIpResource.class,
      ControlledAzureIpHandler::getHandler),
  CONTROLLED_AZURE_RELAY_NAMESPACE(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_RELAY_NAMESPACE",
      ApiResourceType.AZURE_RELAY_NAMESPACE,
      ControlledAzureRelayNamespaceResource.class,
      ControlledAzureRelayNamespaceHandler::getHandler),
  CONTROLLED_AZURE_DISK(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_DISK",
      ApiResourceType.AZURE_DISK,
      ControlledAzureDiskResource.class,
      ControlledAzureDiskHandler::getHandler),
  CONTROLLED_AZURE_NETWORK(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_NETWORK",
      ApiResourceType.AZURE_NETWORK,
      ControlledAzureNetworkResource.class,
      ControlledAzureNetworkHandler::getHandler),
  CONTROLLED_AZURE_VM(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_VM",
      ApiResourceType.AZURE_VM,
      ControlledAzureVmResource.class,
      ControlledAzureVmHandler::getHandler),
  CONTROLLED_AZURE_STORAGE_ACCOUNT(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_STORAGE_ACCOUNT",
      ApiResourceType.AZURE_STORAGE_ACCOUNT,
      ControlledAzureStorageResource.class,
      ControlledAzureStorageHandler::getHandler),
  CONTROLLED_AZURE_STORAGE_CONTAINER(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_STORAGE_CONTAINER",
      ApiResourceType.AZURE_STORAGE_CONTAINER,
      ControlledAzureStorageContainerResource.class,
      ControlledAzureStorageContainerHandler::getHandler),
  CONTROLLED_AZURE_BATCH_POOL(
      CloudPlatform.AZURE,
      StewardshipType.CONTROLLED,
      "CONTROLLED_AZURE_BATCH_POOL",
      ApiResourceType.AZURE_BATCH_POOL,
      ControlledAzureBatchPoolResource.class,
      ControlledAzureBatchPoolHandler::getHandler),
  CONTROLLED_AWS_BUCKET(
          CloudPlatform.AWS,
          StewardshipType.CONTROLLED,
          "CONTROLLED_AWS_BUCKET",
          ApiResourceType.AWS_BUCKET,
          ControlledAwsBucketResource.class,
          ControlledAwsBucketHandler::getHandler),
  CONTROLLED_AWS_SAGEMAKER_NOTEBOOK(
          CloudPlatform.AWS,
          StewardshipType.CONTROLLED,
          "CONTROLLED_AWS_SAGEMAKER_NOTEBOOK",
          ApiResourceType.AWS_SAGEMAKER_NOTEBOOK,
          ControlledAwsSageMakerNotebookResource.class,
          ControlledAwsSageMakerNotebookHandler::getHandler);

  private final CloudPlatform cloudPlatform;
  private final StewardshipType stewardshipType;
  private final String dbString; // serialized form of the resource type
  private final ApiResourceType apiResourceType;
  private final Class<? extends WsmResource> resourceClass;
  private final Supplier<WsmResourceHandler> resourceHandlerSupplier;

  WsmResourceType(
      CloudPlatform cloudPlatform,
      StewardshipType stewardshipType,
      String dbString,
      ApiResourceType apiResourceType,
      Class<? extends WsmResource> resourceClass,
      Supplier<WsmResourceHandler> resourceHandlerSupplier) {
    this.cloudPlatform = cloudPlatform;
    this.stewardshipType = stewardshipType;
    this.dbString = dbString;
    this.apiResourceType = apiResourceType;
    this.resourceClass = resourceClass;
    this.resourceHandlerSupplier = resourceHandlerSupplier;
  }

  /**
   * Translate the database resource type string into the resource type
   *
   * @param dbString string from the database
   * @return resource type
   */
  public static WsmResourceType fromSql(String dbString) {
    for (WsmResourceType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching resource type for " + dbString);
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public StewardshipType getStewardshipType() {
    return stewardshipType;
  }

  public Class<? extends WsmResource> getResourceClass() {
    return resourceClass;
  }

  public WsmResourceHandler getResourceHandler() {
    return resourceHandlerSupplier.get();
  }

  public String toSql() {
    return dbString;
  }

  public ApiResourceType toApiModel() {
    return apiResourceType;
  }
}
