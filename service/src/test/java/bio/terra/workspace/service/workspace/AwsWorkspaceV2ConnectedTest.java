package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.mocks.MockAwsApi;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderResource;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.regions.Region;

@Tag("aws-connected")
public class AwsWorkspaceV2ConnectedTest extends BaseAwsConnectedTest {
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired MockAwsApi mockAwsApi;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  void createDeleteWorkspaceV2WithContextTest() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    // create workspace (with cloud context)
    ApiCreateWorkspaceV2Result createResult =
        mockWorkspaceV2Api.createWorkspaceAndWait(userRequest, apiCloudPlatform);
    assertEquals(StatusEnum.SUCCEEDED, createResult.getJobReport().getStatus());
    UUID workspaceUuid = createResult.getWorkspaceId();

    // cloud context should have been created
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isPresent());
    AwsCloudContext createdCloudContext =
        awsCloudContextService.getAwsCloudContext(workspaceUuid).get();
    AwsTestUtils.assertAwsCloudContextFields(
        awsConnectedTestUtils.getEnvironment().getMetadata(),
        createdCloudContext.getContextFields());
    AwsTestUtils.assertCloudContextCommonFields(
        createdCloudContext.getCommonFields(),
            DEFAULT_GCP_SPEND_PROFILE_ID,
        WsmResourceState.READY,
        null);

    // create resource and verify
    ApiAwsS3StorageFolderCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
            ControlledAwsResourceFixtures.uniqueStorageName());
    UUID resourceUuid =
        mockAwsApi
            .createControlledAwsS3StorageFolder(userRequest, workspaceUuid, creationParameters)
            .getAwsS3StorageFolder()
            .getMetadata()
            .getResourceId();

    ApiAwsS3StorageFolderResource fetchedResource =
        mockAwsApi.getControlledAwsS3StorageFolder(userRequest, workspaceUuid, resourceUuid);
    String expectedBucketName =
        awsConnectedTestUtils
            .getEnvironment()
            .getLandingZone(Region.of(creationParameters.getRegion()))
            .orElseThrow()
            .getStorageBucket()
            .name();
    assertEquals(expectedBucketName, fetchedResource.getAttributes().getBucketName());
    assertEquals(creationParameters.getFolderName(), fetchedResource.getAttributes().getPrefix());

    // delete workspace (with cloud context)
    ApiJobResult deleteResult =
        mockWorkspaceV2Api.deleteWorkspaceAndWait(userRequest, workspaceUuid);
    assertEquals(StatusEnum.SUCCEEDED, deleteResult.getJobReport().getStatus());

    // cloud context should have been deleted
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

    // resource should have been deleted
    assertThrows(
        ResourceNotFoundException.class,
        () -> controlledResourceService.getControlledResource(workspaceUuid, resourceUuid));
  }
}
