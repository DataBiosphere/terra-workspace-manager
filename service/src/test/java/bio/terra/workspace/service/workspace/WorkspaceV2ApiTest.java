package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.testutils.MockMvcUtils;
import bio.terra.workspace.common.testutils.MvcWorkspaceApi;
import bio.terra.workspace.connected.UserAccessTestUtils;
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
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;
  @Autowired UserAccessTestUtils userAccessTestUtils;

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
        userAccessTestUtils.defaultUser().getAuthenticatedRequest();
    // Create the workspace with no cloud context
    ApiCreateWorkspaceV2Result result =
        mvcWorkspaceApi.createWorkspaceAndWait(defaultUserRequest, cloudPlatform);
    UUID workspaceUuid = result.getWorkspaceId();

    mvcWorkspaceApi.deleteWorkspaceAndWait(defaultUserRequest, workspaceUuid);
  }
}
