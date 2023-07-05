package bio.terra.workspace.service.workspace.flight.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.MvcAwsApi;
import bio.terra.workspace.common.utils.MvcWorkspaceApi;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("aws-connected")
public class CreateAwsWorkspaceFlightTest extends BaseAwsConnectedTest {
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;
  @Autowired MvcAwsApi mvcAwsApi;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  void createDeleteWorkspaceWithContextTest() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    // create workspace with cloud context
    ApiCreateWorkspaceV2Result createResult =
        mvcWorkspaceApi.createWorkspaceAndWait(userRequest, apiCloudPlatform);
    assertEquals(StatusEnum.SUCCEEDED, createResult.getJobReport().getStatus());
    UUID workspaceUuid = createResult.getWorkspaceId();

    // flight should have created a cloud context
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isPresent());
    assertEquals(
        /* expected */ awsConnectedTestUtils.getAwsCloudContext(),
        awsCloudContextService.getAwsCloudContext(workspaceUuid).get());

    // create resource and verify
    ApiAwsS3StorageFolderCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
            ControlledAwsResourceFixtures.uniqueStorageName());

    UUID resourceUuid =
        mvcAwsApi
            .createControlledAwsS3StorageFolder(userRequest, workspaceUuid, creationParameters)
            .getAwsS3StorageFolder()
            .getMetadata()
            .getResourceId();
    ApiAwsS3StorageFolderResource fetchedResource =
        mvcAwsApi.getControlledAwsS3StorageFolder(userRequest, workspaceUuid, resourceUuid);
    assertEquals(creationParameters.getFolderName(), fetchedResource.getAttributes().getPrefix());

    // delete workspace (with cloud context)
    ApiJobResult deleteResult = mvcWorkspaceApi.deleteWorkspaceAndWait(userRequest, workspaceUuid);
    assertEquals(StatusEnum.SUCCEEDED, deleteResult.getJobReport().getStatus());

    // cloud context should have been deleted
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

    // resource should have been deleted
    assertThrows(
        ResourceNotFoundException.class,
        () -> controlledResourceService.getControlledResource(workspaceUuid, resourceUuid));
  }
}
