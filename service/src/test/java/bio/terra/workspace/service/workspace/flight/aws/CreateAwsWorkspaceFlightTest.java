package bio.terra.workspace.service.workspace.flight.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.utils.MvcWorkspaceApi;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("aws-connected")
public class CreateAwsWorkspaceFlightTest extends BaseAwsConnectedTest {
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  void successCreatesWorkspaceWithCloudContext() throws Exception {
    ApiCreateWorkspaceV2Result result =
        mvcWorkspaceApi.createWorkspaceAndWait(
            userAccessUtils.defaultUser().getAuthenticatedRequest(), apiCloudPlatform);
    UUID workspaceUuid = result.getWorkspaceId();

    // Flight should have created a cloud context.
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isPresent());
    AwsCloudContext awsCloudContext =
        awsCloudContextService.getAwsCloudContext(workspaceUuid).get();

    assertEquals(
        awsCloudContext.getMajorVersion(), awsTestUtils.getAwsCloudContext().getMajorVersion());
    assertEquals(
        awsCloudContext.getOrganizationId(), awsTestUtils.getAwsCloudContext().getOrganizationId());
    assertEquals(awsCloudContext.getAccountId(), awsTestUtils.getAwsCloudContext().getAccountId());
    assertEquals(
        awsCloudContext.getTenantAlias(), awsTestUtils.getAwsCloudContext().getTenantAlias());
    assertEquals(
        awsCloudContext.getEnvironmentAlias(),
        awsTestUtils.getAwsCloudContext().getEnvironmentAlias());
  }
}
