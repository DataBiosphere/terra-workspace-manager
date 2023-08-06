package bio.terra.workspace.common.mocks;

import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAwsS3StorageFolderRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAwsS3StorageFolder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MockAwsApi {

  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private ObjectMapper objectMapper;

  // S3 folder
  public static final String CREATE_CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/storageFolder";
  public static final String CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT =
      CREATE_CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT + "/%s";

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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledAwsS3StorageFolder.class);
  }

  public ApiAwsS3StorageFolderResource getControlledAwsS3StorageFolder(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, CONTROLLED_AWS_STORAGE_FOLDER_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiAwsS3StorageFolderResource.class);
  }

  // SageMaker Notebook
  public static final String CREATE_CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/notebook";
  public static final String CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT =
      CREATE_CONTROLLED_AWS_NOTEBOOK_PATH_FORMAT + "/%s";
}
