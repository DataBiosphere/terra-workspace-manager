package bio.terra.workspace.service.resource.controlled.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.create.StoreMetadataStep;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
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
    Workspace workspace =
        Workspace.builder()
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .workspaceId(workspaceUuid)
            .userFacingId("a" + workspaceUuid)
            .build();
    workspaceDao.createWorkspace(workspace, /* applicationIds= */ null);

    gcpCloudContextService.createGcpCloudContextStart(workspaceUuid, "flight-testentersinfo");
    gcpCloudContextService.createGcpCloudContextFinish(
        workspaceUuid, new GcpCloudContext("fake-project"), "flight-testentersinfo");

    StoreMetadataStep storeGoogleBucketMetadataStep = new StoreMetadataStep(resourceDao);

    // Stub the flight map as of this step
    ControlledGcsBucketResource bucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();

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
    assertThat(bucketResource, equalTo(daoBucket));
  }
}
