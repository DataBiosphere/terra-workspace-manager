package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.MvcWorkspaceApi;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Test the WorkspaceV2 endpoints
@Tag("connected")
public class WorkspaceV2ApiTest extends BaseConnectedTest {
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  public void testAsyncDeleteWorkspace() throws Exception {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();
    ApiCreatedWorkspace workspace =
        mockMvcUtils.createWorkspaceWithoutCloudContext(defaultUserRequest);
    mvcWorkspaceApi.deleteWorkspaceAndWait(defaultUserRequest, workspace.getId());
  }

  @Test
  public void testAsyncDeleteGcpCloudContext() throws Exception {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();
    ApiCreatedWorkspace workspace =
        mockMvcUtils.createWorkspaceWithCloudContext(defaultUserRequest);
    mvcWorkspaceApi.deleteCloudContextAndWait(
        defaultUserRequest, workspace.getId(), CloudPlatform.GCP);
    mvcWorkspaceApi.deleteWorkspaceAndWait(defaultUserRequest, workspace.getId());
  }
}
