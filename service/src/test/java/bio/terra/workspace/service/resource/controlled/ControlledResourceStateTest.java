package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.getGoogleBucketCreationParameters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseUnitTestMockGcpCloudContextService;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateSamResourceStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ControlledResourceStateTest extends BaseUnitTestMockGcpCloudContextService {
  private static final Logger logger = LoggerFactory.getLogger(ControlledResourceStateTest.class);
  @Autowired JobService jobService;
  @Autowired ControlledResourceService controlledResourceService;
  @Autowired WorkspaceDao workspaceDao;
  @Autowired ResourceDao resourceDao;
  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  @BeforeEach
  public void setup() throws IOException {
    when(mockGcpCloudContextService().getRequiredGcpProject(any())).thenReturn(FAKE_PROJECT_ID);
  }

  @Test
  public void testCreateBucketFailBroken() throws Exception {
    // Get the resource from database and check state
    WsmResource dbResource = testCreateBucketFailedState(WsmResourceStateRule.BROKEN_ON_FAILURE);
    assertNotNull(dbResource);
    assertEquals(WsmResourceState.BROKEN, dbResource.getState());
  }

  @Test
  public void testCreateBucketFailDeleted() throws Exception {
    // Get the resource from database and check state
    WsmResource dbResource = testCreateBucketFailedState(WsmResourceStateRule.DELETE_ON_FAILURE);
    assertNull(dbResource);
  }

  private WsmResource testCreateBucketFailedState(WsmResourceStateRule rule) throws Exception {
    when(mockFeatureConfiguration().getStateRule()).thenReturn(rule);

    UUID workspaceId = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceId).build();
    ApiGcpGcsBucketCreationParameters creationParameters = getGoogleBucketCreationParameters();

    // Inject an error
    Map<String, StepStatus> failStep = new HashMap<>();
    failStep.put(CreateSamResourceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(failStep).build());

    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                resource, null, MockMvcUtils.USER_REQUEST, creationParameters));

    try {
      return resourceDao.getResource(workspaceId, resource.getResourceId());
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }
}
