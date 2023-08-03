package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Test the WorkspaceV2 endpoints
@Tag("connected")
public class WorkspaceV2ApiTest extends BaseConnectedTest {
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  public void testAsyncCreateDeleteWorkspace_noCloudContext() throws Exception {
    createDeleteOperation(null);
  }

  @Test
  public void testAsyncCreateDeleteWorkspace_withCloudContext() throws Exception {
    createDeleteOperation(ApiCloudPlatform.GCP);
  }

  private void createDeleteOperation(ApiCloudPlatform cloudPlatform) throws Exception {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();
    // Create the workspace with no cloud context
    ApiCreateWorkspaceV2Result result =
        mockWorkspaceV2Api.createWorkspaceAndWait(defaultUserRequest, cloudPlatform);
    UUID workspaceUuid = result.getWorkspaceId();

    mockWorkspaceV2Api.deleteWorkspaceAndWait(defaultUserRequest, workspaceUuid);
  }
}
