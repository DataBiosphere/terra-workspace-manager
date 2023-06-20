package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID;
import static software.amazon.awssdk.services.sagemaker.model.InstanceType.ML_T2_MEDIUM;

import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields.AwsCloudContextV1;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpResponse;

public class ControlledAwsResourceFixtures {

  public static final long V1_VERSION = AwsCloudContextV1.getVersion();
  public static final String MAJOR_VERSION = "v0.5.8";
  public static final String ORGANIZATION_ID = "o-organization";
  public static final String ACCOUNT_ID = "1245893245";
  public static final String TENANT_ALIAS = "tenant-saas";
  public static final String ENVIRONMENT_ALIAS = "unit-test-env";
  public static final String AWS_REGION = "us-east-1";

  public static final SdkHttpResponse sdkHttpResponse2xx =
      SdkHttpResponse.builder().statusCode(200).build();
  public static final SdkHttpResponse sdkHttpResponse4xx =
      SdkHttpResponse.builder().statusCode(409).build();
  public static final SdkHttpResponse sdkHttpResponse5xx =
      SdkHttpResponse.builder().statusCode(500).build();

  public static final AwsCredentialsProvider credentialProvider =
      AnonymousCredentialsProvider.create();

  // Cloud context

  public static AwsCloudContext makeAwsCloudContext() {
    return new AwsCloudContext(
        new AwsCloudContextFields(
            MAJOR_VERSION, ORGANIZATION_ID, ACCOUNT_ID, TENANT_ALIAS, ENVIRONMENT_ALIAS),
        new CloudContextCommonFields(
            DEFAULT_SPEND_PROFILE_ID, WsmResourceState.READY, /*flightId=*/ null, /*error=*/ null));
  }

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
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .name(resourceName)
                .region(AWS_REGION)
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
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .name(resourceName)
                .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                .region(AWS_REGION)
                .build())
        .instanceName(instanceName)
        .instanceType(ML_T2_MEDIUM.toString());
  }
}
