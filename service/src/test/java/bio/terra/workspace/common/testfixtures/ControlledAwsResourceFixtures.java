package bio.terra.workspace.common.testfixtures;

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
    return ControlledAwsS3StorageFolderResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().workspaceUuid(workspaceUuid).build())
        .bucketName("foo")
        .prefix("bar")
        .build();
  }

  // Sagemaker Notebook

  public static ControlledAwsSageMakerNotebookResource makeDefaultAwsSagemakerNotebookResource(
      UUID workspaceUuid) {
    return ControlledAwsSageMakerNotebookResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                .build())
        .instanceType(ML_T2_MEDIUM.toString())
        .instanceName("foo")
        .build();
  }
}
