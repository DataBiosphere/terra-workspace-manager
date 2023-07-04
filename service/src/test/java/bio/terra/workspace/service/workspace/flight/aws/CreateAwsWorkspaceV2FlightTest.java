package bio.terra.workspace.service.workspace.flight.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.MvcWorkspaceApi;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("aws-connected")
public class CreateAwsWorkspaceV2FlightTest extends BaseAwsConnectedTest {
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;
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

    // TODO(BENCH-715) add storage bucket

    // delete workspace (with cloud context)
    ApiJobResult deleteResult = mvcWorkspaceApi.deleteWorkspaceAndWait(userRequest, workspaceUuid);
    assertEquals(StatusEnum.SUCCEEDED, deleteResult.getJobReport().getStatus());

    // cloud context should have been deleted
    assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());

    // TODO(BENCH-715) check storage bucket deleted
  }
}
