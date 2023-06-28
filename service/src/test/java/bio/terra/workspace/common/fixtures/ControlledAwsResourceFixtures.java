package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static software.amazon.awssdk.services.sagemaker.model.InstanceType.ML_T2_MEDIUM;

import bio.terra.workspace.common.utils.TestUtils;
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
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sagemaker.model.StartNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.StopNotebookInstanceResponse;

public class ControlledAwsResourceFixtures {
  public static final long V1_VERSION = AwsCloudContextV1.getVersion();
  public static final String MAJOR_VERSION = "v0.5.8";
  public static final String ORGANIZATION_ID = "o-organization";
  public static final String ACCOUNT_ID = "1245893245";
  public static final String TENANT_ALIAS = "tenant-saas";
  public static final String ENVIRONMENT_ALIAS = "unit-test-env";
  public static final String AWS_REGION = "us-east-1";
  public static final String AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN =
      "arn:aws:iam::01234567890:role/NotebookRole";
  public static final String AWS_LANDING_ZONE_KMS_KEY_ARN =
      "arn:aws:iam::12345678900:role/KmsKeyRole";
  public static final String AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN =
      "arn:aws:iam::23456789001:role/KmsKeyRole";
  public static final SdkHttpResponse SDK_HTTP_RESPONSE_200 =
      SdkHttpResponse.builder().statusCode(200).build();
  public static final SdkHttpResponse SDK_HTTP_RESPONSE_400 =
      SdkHttpResponse.builder().statusCode(400).build();
  public static final AwsCredentialsProvider AWS_CREDENTIALS_PROVIDER =
      AnonymousCredentialsProvider.create();
  public static final AwsServiceException AWS_SERVICE_EXCEPTION_1 =
      AwsServiceException.builder().message("ResourceNotFoundException").build();
  public static final AwsServiceException AWS_SERVICE_EXCEPTION_2 =
      AwsServiceException.builder().message("not authorized to perform").build();

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
            workspaceUuid, TestUtils.appendRandomNumber("s3-resource"), "foo-bucket", "bar-prefix")
        .build();
  }

  public static ControlledAwsS3StorageFolderResource.Builder makeAwsS3StorageFolderResourceBuilder(
      String bucket, String prefix) {
    return makeAwsS3StorageFolderResourceBuilder(
        WORKSPACE_ID, TestUtils.appendRandomNumber("s3-resource"), bucket, prefix);
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

  public static final CreateNotebookInstanceResponse createNotebookResponse200 =
      (CreateNotebookInstanceResponse)
          CreateNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_200).build();
  public static final CreateNotebookInstanceResponse createNotebookResponse400 =
      (CreateNotebookInstanceResponse)
          CreateNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final DescribeNotebookInstanceResponse describeNotebookResponse200InService =
      (DescribeNotebookInstanceResponse)
          DescribeNotebookInstanceResponse.builder()
              .notebookInstanceStatus(NotebookInstanceStatus.IN_SERVICE)
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final DescribeNotebookInstanceResponse describeNotebookResponse200Stopped =
      (DescribeNotebookInstanceResponse)
          DescribeNotebookInstanceResponse.builder()
              .notebookInstanceStatus(NotebookInstanceStatus.STOPPED)
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final DescribeNotebookInstanceResponse describeNotebookResponse400 =
      (DescribeNotebookInstanceResponse)
          DescribeNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final StartNotebookInstanceResponse startNotebookResponse200 =
      (StartNotebookInstanceResponse)
          StartNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_200).build();
  public static final StartNotebookInstanceResponse startNotebookResponse400 =
      (StartNotebookInstanceResponse)
          StartNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final StopNotebookInstanceResponse stopNotebookResponse200 =
      (StopNotebookInstanceResponse)
          StopNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_200).build();
  public static final StopNotebookInstanceResponse stopNotebookResponse400 =
      (StopNotebookInstanceResponse)
          StopNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final DeleteNotebookInstanceResponse deleteNotebookResponse200 =
      (DeleteNotebookInstanceResponse)
          DeleteNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_200).build();
  public static final DeleteNotebookInstanceResponse deleteNotebookResponse400 =
      (DeleteNotebookInstanceResponse)
          DeleteNotebookInstanceResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final WaiterResponse waiterNotebookResponse =
      DefaultWaiterResponse.builder()
          .attemptsExecuted(1)
          .response(DescribeNotebookInstanceResponse.builder().build())
          .build(); // wait successful
  public static final WaiterResponse waiterNotebookException =
      DefaultWaiterResponse.builder()
          .attemptsExecuted(1)
          .exception(AWS_SERVICE_EXCEPTION_2)
          .build(); // wait failure

  public static ControlledAwsSageMakerNotebookResource makeDefaultAwsSagemakerNotebookResource(
      UUID workspaceUuid) {
    return makeAwsSageMakerNotebookResourceBuilder(
            workspaceUuid, TestUtils.appendRandomNumber("sagemaker-resource"), "foo-instance")
        .build();
  }

  public static ControlledAwsSageMakerNotebookResource.Builder
      makeAwsSageMakerNotebookResourceBuilder(String instanceName) {
    return makeAwsSageMakerNotebookResourceBuilder(
        WORKSPACE_ID, TestUtils.appendRandomNumber("sagemaker-resource"), instanceName);
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
