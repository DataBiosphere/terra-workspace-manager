package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsS3StorageFolder;
import bio.terra.workspace.generated.model.ApiUpdateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MvcAwsApi extends MockMvcUtils {

  public static final String CONTROLLED_AWS_STORAGE_FOLDER_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/storageFolder";

  /*
  s3 cloud name
   */

  public ApiCreatedControlledAwsS3StorageFolder createControlledAwsS3StorageFolder(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      ApiAwsS3StorageFolderCreationParameters creationParameters)
      throws Exception {
    ApiCreateControlledAwsS3StorageFolderRequestBody requestBody =
        new ApiCreateControlledAwsS3StorageFolderRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .name(creationParameters.getFolderName()))
            .awsS3StorageFolder(creationParameters);

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_AWS_STORAGE_FOLDER_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledAwsS3StorageFolder.class);
  }

  public ApiAwsS3StorageFolderResource getControlledAwsS3StorageFolder(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(
            userRequest,
            CONTROLLED_AWS_STORAGE_FOLDER_V1_PATH_FORMAT + "/%s",
            workspaceId,
            resourceId);
    return objectMapper.readValue(serializedResponse, ApiAwsS3StorageFolderResource.class);
  }

  public ApiAwsS3StorageFolderResource updateControlledAwsS3StorageFolder(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription)
      throws Exception {
    ApiUpdateControlledAwsS3StorageFolderRequestBody requestBody =
        new ApiUpdateControlledAwsS3StorageFolderRequestBody()
            .name(newName)
            .description(newDescription);

    String serializedResponse =
        getSerializedResponseForPatch(
            userRequest,
            CONTROLLED_AWS_STORAGE_FOLDER_V1_PATH_FORMAT,
            workspaceId,
            resourceId,
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiAwsS3StorageFolderResource.class);
  }

  /*
  public ApiDeleteControlledAwsResourceResult deleteControlledAwsS3StorageFolder(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiDeleteControlledAwsResourceRequestBody requestBody =
        new ApiDeleteControlledAwsResourceRequestBody().jobControl(new ApiJobControl().id(jobId));

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_AWS_STORAGE_FOLDER_V1_PATH_FORMAT,
            workspaceId,
            resourceId,
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiAwsS3StorageFolderResource.class);
  }


  private ApiCreateCloudContextResult createCloudContext(
      AuthenticatedUserRequest userRequest, UUID workspaceId, ApiCloudPlatform apiCloudPlatform)
      throws Exception {

    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(apiCloudPlatform)
            .jobControl(new ApiJobControl().id(jobId));
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CREATE_CLOUD_CONTEXT_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  private ApiCreateCloudContextResult getCreateCloudContextResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, GET_CLOUD_CONTEXT_PATH_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }



  /*
  delete s3
  get delete reseult
   */

  /*
  s3 credential
   */
}
