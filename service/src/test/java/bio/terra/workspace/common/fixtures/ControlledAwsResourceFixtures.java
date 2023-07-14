package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static software.amazon.awssdk.services.sagemaker.model.InstanceType.ML_T2_MEDIUM;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.aws.resource.discovery.LandingZone;
import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook.ControlledAwsSageMakerNotebookResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields.AwsCloudContextV1;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sagemaker.model.CreateNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.DeleteNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sagemaker.model.StartNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.StopNotebookInstanceResponse;
import software.amazon.awssdk.services.sts.model.Tag;

public class ControlledAwsResourceFixtures {
  public static final long V1_VERSION = AwsCloudContextV1.getVersion();
  public static final String MAJOR_VERSION = "v0.5.8";
  public static final String ORGANIZATION_ID = "o-organization";
  public static final String ACCOUNT_ID = "1245893245";
  public static final String TENANT_ALIAS = "tenant-saas";
  public static final String ENVIRONMENT_ALIAS = "unit-test-env";
  public static final String AWS_REGION = "us-east-1";
  public static final String AWS_ENVIRONMENT_WSM_ROLE_ARN =
      "arn:aws:iam::10000000001:role/WorkspaceManagerRole";
  public static final String AWS_ENVIRONMENT_USER_ROLE_ARN =
      "arn:aws:iam::20000000002:role/UserRole";
  public static final String AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN =
      "arn:aws:iam::30000000003:role/NotebookRole";
  public static final String AWS_LANDING_ZONE_STORAGE_BUCKET_ARN =
      "arn:aws:iam::40000000004:role/StorageBucket";
  public static final String AWS_LANDING_ZONE_KMS_KEY_ARN = "arn:aws:iam::50000000005:role/KmsKey";
  public static final String AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN =
      "arn:aws:iam::60000000006:role/NotebookLifecycleConfig";
  public static final SdkHttpResponse SDK_HTTP_RESPONSE_200 =
      SdkHttpResponse.builder().statusCode(200).statusText("Success").build();
  public static final SdkHttpResponse SDK_HTTP_RESPONSE_400 =
      SdkHttpResponse.builder().statusCode(400).statusText("Bad Request").build();
  public static final AwsCredentialsProvider AWS_CREDENTIALS_PROVIDER =
      AnonymousCredentialsProvider.create();
  public static final AwsServiceException AWS_SERVICE_EXCEPTION_1 =
      AwsServiceException.builder().message("ResourceNotFoundException").build();
  public static final AwsServiceException AWS_SERVICE_EXCEPTION_2 =
      AwsServiceException.builder().message("not authorized to perform").build();

  public static final Metadata AWS_METADATA =
      Metadata.builder()
          .tenantAlias(TENANT_ALIAS)
          .organizationId(ORGANIZATION_ID)
          .environmentAlias(ENVIRONMENT_ALIAS)
          .accountId(ACCOUNT_ID)
          .region(Region.of(AWS_REGION))
          .majorVersion(MAJOR_VERSION)
          .tagMap(Map.of("tagKey", "tagValue"))
          .build();
  public static final LandingZone AWS_LANDING_ZONE =
      LandingZone.builder()
          .metadata(AWS_METADATA)
          .storageBucket(Arn.fromString(AWS_LANDING_ZONE_STORAGE_BUCKET_ARN), "bucket")
          .kmsKey(Arn.fromString(AWS_LANDING_ZONE_KMS_KEY_ARN), UUID.randomUUID())
          .addNotebookLifecycleConfiguration(
              Arn.fromString(AWS_LANDING_ZONE_NOTEBOOK_LIFECYCLE_CONFIG_ARN), "lifecycleConfig")
          .build();
  public static final Environment AWS_ENVIRONMENT =
      Environment.builder()
          .metadata(AWS_METADATA)
          .workspaceManagerRoleArn(Arn.fromString(AWS_ENVIRONMENT_WSM_ROLE_ARN))
          .userRoleArn(Arn.fromString(AWS_ENVIRONMENT_USER_ROLE_ARN))
          .notebookRoleArn(Arn.fromString(AWS_ENVIRONMENT_NOTEBOOK_ROLE_ARN))
          .addLandingZone(AWS_LANDING_ZONE.getMetadata().getRegion(), AWS_LANDING_ZONE)
          .build();

  public static Collection<Tag> makeTags() {
    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendResourceTags(tags, makeAwsCloudContext(), null);
    return tags;
  }

  // Cloud context

  public static AwsCloudContext makeAwsCloudContext() {
    return new AwsCloudContext(
        new AwsCloudContextFields(
            MAJOR_VERSION, ORGANIZATION_ID, ACCOUNT_ID, TENANT_ALIAS, ENVIRONMENT_ALIAS),
        new CloudContextCommonFields(
            DEFAULT_SPEND_PROFILE_ID, WsmResourceState.READY, /*flightId=*/ null, /*error=*/ null));
  }

  // S3 Folder

  public static final S3Object s3Obj1 = S3Object.builder().key("key1").build();
  public static final S3Object s3Obj2 = S3Object.builder().key("key2").build();

  public static final PutObjectResponse putFolderResponse200 =
      (PutObjectResponse)
          PutObjectResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_200).build();
  public static final PutObjectResponse putFolderResponse400 =
      (PutObjectResponse)
          PutObjectResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final ListObjectsV2Response listFolderResponse200_2 =
      (ListObjectsV2Response)
          ListObjectsV2Response.builder()
              .contents(List.of(s3Obj1, s3Obj2))
              .isTruncated(false)
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final ListObjectsV2Response listFolderResponse200_1_obj1 =
      (ListObjectsV2Response)
          ListObjectsV2Response.builder()
              .contents(List.of(s3Obj1))
              .isTruncated(true)
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final ListObjectsV2Response listFolderResponse200_1_obj2 =
      (ListObjectsV2Response)
          ListObjectsV2Response.builder()
              .contents(List.of(s3Obj2))
              .isTruncated(false)
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final ListObjectsV2Response listFolderResponse200_0 =
      (ListObjectsV2Response)
          ListObjectsV2Response.builder()
              .isTruncated(false)
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final ListObjectsV2Response listFolderResponse400 =
      (ListObjectsV2Response)
          ListObjectsV2Response.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static final DeleteObjectsResponse deleteFolderResponse200 =
      (DeleteObjectsResponse)
          DeleteObjectsResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_200).build();
  public static final DeleteObjectsResponse deleteFolderResponse200_1_obj1 =
      (DeleteObjectsResponse)
          DeleteObjectsResponse.builder()
              .errors(S3Error.builder().key(ControlledAwsResourceFixtures.s3Obj1.key()).build())
              .sdkHttpResponse(SDK_HTTP_RESPONSE_200)
              .build();
  public static final DeleteObjectsResponse deleteFolderResponse400 =
      (DeleteObjectsResponse)
          DeleteObjectsResponse.builder().sdkHttpResponse(SDK_HTTP_RESPONSE_400).build();

  public static String uniqueStorageName() {
    return TestUtils.appendRandomNumber("wsmTestAwsS3Folder");
  }

  public static ApiAwsS3StorageFolderCreationParameters makeAwsS3StorageFolderCreationParameters(
      String storageName) {
    return new ApiAwsS3StorageFolderCreationParameters().folderName(storageName).region(AWS_REGION);
  }

  public static ControlledAwsS3StorageFolderResource makeResource(
      UUID workspaceUuid,
      String bucket,
      ApiAwsS3StorageFolderCreationParameters creationParameters) {
    return ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
            workspaceUuid,
            /* resourceName= */ creationParameters.getFolderName(),
            bucket,
            /* folderName= */ creationParameters.getFolderName())
        .build();
  }

  public static ControlledAwsS3StorageFolderResource makeDefaultAwsS3StorageFolderResource(
      UUID workspaceUuid) {
    String storageName = uniqueStorageName();
    return makeAwsS3StorageFolderResourceBuilder(
            workspaceUuid,
            /* resourceName= */ storageName,
            "foo-bucket",
            /* folderName= */ storageName)
        .build();
  }

  public static ControlledAwsS3StorageFolderResource.Builder makeAwsS3StorageFolderResourceBuilder(
      UUID workspaceUuid, String resourceName, String bucket, String folderName) {
    return ControlledAwsS3StorageFolderResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .name(resourceName)
                .region(AWS_REGION)
                .build())
        .bucketName(bucket)
        .prefix(folderName);
  }

  // Sagemaker Notebook

  public static final String SAGEMAKER_INSTANCE_TYPE = ML_T2_MEDIUM.toString();

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

  public static final WaiterResponse<DescribeNotebookInstanceResponse> waiterNotebookResponse =
      DefaultWaiterResponse.<DescribeNotebookInstanceResponse>builder()
          .attemptsExecuted(1)
          .response(DescribeNotebookInstanceResponse.builder().build())
          .build(); // wait successful
  public static final WaiterResponse<DescribeNotebookInstanceResponse> waiterNotebookException_1 =
      DefaultWaiterResponse.<DescribeNotebookInstanceResponse>builder()
          .attemptsExecuted(1)
          .exception(AWS_SERVICE_EXCEPTION_1)
          .build(); // wait failure
  public static final WaiterResponse<DescribeNotebookInstanceResponse> waiterNotebookException_2 =
      DefaultWaiterResponse.<DescribeNotebookInstanceResponse>builder()
          .attemptsExecuted(1)
          .exception(AWS_SERVICE_EXCEPTION_2)
          .build(); // wait failure

  public static String getUniqueNotebookName() {
    return TestUtils.appendRandomNumber("wsmTestAwsSageMaker");
  }

  public static ApiAwsSageMakerNotebookCreationParameters
      makeAwsSageMakerNotebookCreationParameters(String instanceName) {
    return new ApiAwsSageMakerNotebookCreationParameters()
        .instanceName(instanceName)
        .instanceType(SAGEMAKER_INSTANCE_TYPE)
        .region(AWS_REGION);
  }

  public static ControlledAwsSageMakerNotebookResource makeResource(
      UUID workspaceUuid,
      ApiAwsSageMakerNotebookCreationParameters creationParameters,
      String userEmail) {
    return ControlledAwsResourceFixtures.makeAwsSageMakerNotebookResourceBuilder(
            workspaceUuid,
            /* resourceName= */ creationParameters.getInstanceName(),
            /* instanceName= */ creationParameters.getInstanceName(),
            userEmail)
        .build();
  }

  public static ControlledAwsSageMakerNotebookResource makeDefaultAwsSagemakerNotebookResource(
      UUID workspaceUuid) {
    String notebookName = getUniqueNotebookName();
    return makeAwsSageMakerNotebookResourceBuilder(
            workspaceUuid,
            /* resourceName= */ notebookName,
            /* instanceName= */ notebookName,
            DEFAULT_USER_EMAIL)
        .build();
  }

  public static ControlledAwsSageMakerNotebookResource.Builder
      makeAwsSageMakerNotebookResourceBuilder(
          UUID workspaceUuid, String resourceName, String instanceName, String userEmail) {
    return ControlledAwsSageMakerNotebookResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .name(resourceName)
                .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                .region(AWS_REGION)
                .assignedUser(userEmail)
                .build())
        .instanceName(instanceName)
        .instanceType(SAGEMAKER_INSTANCE_TYPE);
  }
}
