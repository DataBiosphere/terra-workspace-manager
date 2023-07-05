package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsS3StorageFolder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MvcAwsApi extends MockMvcUtils {

  public static final String CONTROLLED_AWS_STORAGE_FOLDER_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/storageFolder";

  public ApiCreatedControlledAwsS3StorageFolder createControlledAwsS3StorageFolder(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      ApiAwsS3StorageFolderCreationParameters creationParameters)
      throws Exception {
    ApiCreateControlledAwsS3StorageFolderRequestBody requestBody =
        new ApiCreateControlledAwsS3StorageFolderRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .name(resourceName))
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
}
