package bio.terra.workspace.service.resource.controlled.flight;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.buildWorkspace;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.createWorkspaceInDb;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateResourceInDbStartStep;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

public class StoreControlledResourceMetadataStepTest extends BaseUnitTest {
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private ResourceDao resourceDao;

  @Mock private FlightContext mockFlightContext;

  @Test
  public void testEntersInfo() throws InterruptedException, RetryException {
    // Generate a fake workspace and cloud context so that the
    // database insert will pass FK constraints.
    UUID workspaceUuid = UUID.randomUUID();
    Workspace workspace = buildWorkspace(workspaceUuid, WorkspaceStage.RAWLS_WORKSPACE);
    createWorkspaceInDb(workspace, workspaceDao);

    WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(
        workspaceDao, workspaceUuid, WorkspaceUnitTestUtils.PROJECT_ID);

    ControlledGcsBucketResource bucketResource =
        makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    CreateResourceInDbStartStep storeGoogleBucketMetadataStep =
        new CreateResourceInDbStartStep(
            resourceDao, WsmResourceStateRule.DELETE_ON_FAILURE, bucketResource);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(ResourceKeys.RESOURCE, bucketResource);
    inputFlightMap.makeImmutable();

    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult result = storeGoogleBucketMetadataStep.doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));

    WsmResource daoResource =
        resourceDao.getResource(bucketResource.getWorkspaceId(), bucketResource.getResourceId());
    assertThat(daoResource.getResourceType(), equalTo(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET));

    ControlledGcsBucketResource daoBucket = (ControlledGcsBucketResource) daoResource;
    assertTrue(bucketResource.partialEqual(daoBucket));
  }
}
