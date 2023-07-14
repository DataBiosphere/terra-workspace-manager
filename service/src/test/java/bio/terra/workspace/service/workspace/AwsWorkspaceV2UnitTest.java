package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_ENVIRONMENT;
import static bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures.AWS_LANDING_ZONE;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import bio.terra.aws.resource.discovery.S3EnvironmentDiscovery;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

public class AwsWorkspaceV2UnitTest extends BaseAwsUnitTest {

  @Autowired WorkspaceService workspaceService;
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Mock private ControlledResourceService controlledResourceService;
  @Mock private S3EnvironmentDiscovery mockEnvironmentDiscovery;

  @Test
  void createDeleteWorkspaceV2WithContextTest() throws Exception {
    try (MockedStatic<AwsUtils> mockAwsUtils = mockStatic(AwsUtils.class)) {
      when(mockEnvironmentDiscovery.discoverEnvironment()).thenReturn(AWS_ENVIRONMENT);
      mockAwsUtils
          .when(() -> AwsUtils.createEnvironmentDiscovery(any()))
          .thenReturn(mockEnvironmentDiscovery);

      Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
      UUID workspaceUuid = workspace.workspaceId();
      workspaceService.createWorkspaceV2(
          workspace, null, null, CloudPlatform.AWS, DEFAULT_SPEND_PROFILE, UUID.randomUUID().toString(), USER_REQUEST);
      // not wait needed in unit test

      // cloud context should have been created
      assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isPresent());

      // create resource and verify
      mockAwsUtils.when(() -> AwsUtils.checkFolderExists(any(), any())).thenReturn(false);
      mockAwsUtils
          .when(() -> AwsUtils.createStorageFolder(any(), any(), any()))
          .thenAnswer(invocation -> null);

      ApiAwsS3StorageFolderCreationParameters creationParameters =
          ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
              ControlledAwsResourceFixtures.uniqueStorageName());
      ControlledAwsS3StorageFolderResource resource =
          ControlledAwsResourceFixtures.makeResource(
              workspaceUuid, AWS_LANDING_ZONE.getStorageBucket().name(), creationParameters);
     /* controlledResourceService
          .createControlledResourceSync(resource, null, USER_REQUEST, creationParameters)
          .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);

      ControlledAwsS3StorageFolderResource fetchedResource =
          controlledResourceService
              .getControlledResource(workspaceUuid, resource.getResourceId())
              .castByEnum(WsmResourceType.CONTROLLED_AWS_S3_STORAGE_FOLDER);
      assertEquals(resource.getBucketName(), fetchedResource.getBucketName());
      assertEquals(resource.getPrefix(), fetchedResource.getPrefix());

      */

      // delete workspace (with cloud context)
      workspaceService.deleteWorkspaceAsync(workspace, USER_REQUEST, UUID.randomUUID().toString(), "result-path");
      // not wait needed in unit test

      // cloud context should have been deleted
      assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

      // resource should have been deleted
      assertThrows(
          ResourceNotFoundException.class,
          () ->
              controlledResourceService.getControlledResource(
                  workspaceUuid, resource.getResourceId()));
    }
  }
}
