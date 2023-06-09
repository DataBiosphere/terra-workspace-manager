package bio.terra.workspace.common.testfixtures;

import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static software.amazon.awssdk.services.sagemaker.model.InstanceType.ML_T2_MEDIUM;

import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import java.util.UUID;

public class ControlledAwsResourceFixtures {

  // S3 Folder

  public static ControlledAwsS3StorageFolderResource makeDefaultAwsS3StorageFolderResource(
      UUID workspaceUuid) {
    return makeAwsS3StorageFolderResourceBuilder(
            workspaceUuid, "s3-resource", "foo-bucket", "bar-prefix")
        .build();
  }

  public static ControlledAwsS3StorageFolderResource.Builder makeAwsS3StorageFolderResourceBuilder(
      String bucket, String prefix) {
    return makeAwsS3StorageFolderResourceBuilder(WORKSPACE_ID, "foo", bucket, prefix);
  }

  public static ControlledAwsS3StorageFolderResource.Builder makeAwsS3StorageFolderResourceBuilder(
      UUID workspaceUuid, String resourceName, String bucket, String prefix) {
    return ControlledAwsS3StorageFolderResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .name(resourceName)
                .region("us-east-1")
                .build())
        .bucketName(bucket)
        .prefix(prefix);
  }

  // Sagemaker Notebook

  public static ControlledAwsSageMakerNotebookResource makeDefaultAwsSagemakerNotebookResource(
      UUID workspaceUuid) {
    return makeAwsSageMakerNotebookResourceBuilder(
            workspaceUuid, "sagemaker-resource", "foo-instance")
        .build();
  }

  public static ControlledAwsSageMakerNotebookResource.Builder
      makeAwsSageMakerNotebookResourceBuilder(String instanceName) {
    return makeAwsSageMakerNotebookResourceBuilder(
        WORKSPACE_ID, "sagemaker-resource", instanceName);
  }

  public static ControlledAwsSageMakerNotebookResource.Builder
      makeAwsSageMakerNotebookResourceBuilder(
          UUID workspaceUuid, String resourceName, String instanceName) {
    return ControlledAwsSageMakerNotebookResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .name(resourceName)
                .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                .region("us-east-1")
                .build())
        .instanceName(instanceName)
        .instanceType(ML_T2_MEDIUM.toString());
  }
}
