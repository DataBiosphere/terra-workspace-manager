package bio.terra.workspace.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.MvcAwsApi;
import bio.terra.workspace.generated.model.ApiAwsS3StorageFolderCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class ControlledAwsResourceApiControllerTest extends BaseAwsUnitTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MvcAwsApi mvcAwsApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired JobService jobService;
  @Autowired WorkspaceService workspaceService;

  private AuthenticatedUserRequest userRequest;

  private static final UUID workspaceUuid = UUID.randomUUID();

  @BeforeEach
  public void setup() throws InterruptedException {

    /*
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(USER_REQUEST.getEmail());
    when(mockSamService()
        .isAuthorized(
            any(),
            eq(SamConstants.SamResource.SPEND_PROFILE),
            any(),
            eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    // Needed for assertion that requester has role on workspace.
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));

     */
  }

  @Test
  void createS3FolderTest() throws Exception {
    ApiAwsS3StorageFolderCreationParameters creationParameters =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderCreationParameters(
            ControlledAwsResourceFixtures.uniqueStorageName());

    ControlledAwsS3StorageFolderResource resource =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderResource(
            workspaceUuid, "foo-bucket", creationParameters);

    when(getMockControlledResourceService()
            .createControlledResourceSync(
                any(ControlledAwsS3StorageFolderResource.class), any(), any(), any()))
        .thenReturn(resource);

    UUID resourceUuid =
        mvcAwsApi
            .createControlledAwsS3StorageFolder(userRequest, workspaceUuid, creationParameters)
            .getAwsS3StorageFolder()
            .getMetadata()
            .getResourceId();
  }
}
