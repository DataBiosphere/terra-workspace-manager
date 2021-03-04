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
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.create.StoreMetadataStep;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

public class StoreControlledResourceMetadataStepTest extends BaseUnitTest {
  @Autowired private ResourceDao resourceDao;

  @Mock private FlightContext mockFlightContext;

  @Test
  public void testEntersInfo() throws InterruptedException, RetryException {
    StoreMetadataStep storeGoogleBucketMetadataStep = new StoreMetadataStep(resourceDao);

    // Stub the flight map as of this step
    ControlledGcsBucketResource bucketResource =
        ControlledResourceFixtures.makeControlledGcsBucketResource(UUID.randomUUID());

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(JobMapKeys.REQUEST.getKeyName(), bucketResource);
    inputFlightMap.makeImmutable();

    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult result = storeGoogleBucketMetadataStep.doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));

    WsmResource daoResource =
        resourceDao.getResource(bucketResource.getWorkspaceId(), bucketResource.getResourceId());
    assertThat(daoResource.getResourceType(), equalTo(WsmResourceType.GCS_BUCKET));

    ControlledGcsBucketResource daoBucket = (ControlledGcsBucketResource) daoResource;
    assertThat(bucketResource, equalTo(daoBucket));
  }
}
