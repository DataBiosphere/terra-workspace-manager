package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DATA_REFERENCE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.datareference.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceDbModel;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class StoreControlledResourceMetadataStepTest extends BaseUnitTest {

  @Mock private ControlledResourceDao mockControlledResourceDao;
  @Mock private DataReferenceDao mockDataReferenceDao;

  @Mock private FlightContext mockFlightContext;
  @Captor private ArgumentCaptor<ControlledResourceDbModel> dbModelArgumentCaptor;
  @Captor private ArgumentCaptor<DataReferenceRequest> dataReferenceRequestCaptor;
  private StoreControlledResourceMetadataStep storeGoogleBucketMetadataStep;

  @BeforeEach
  public void setup() {
    storeGoogleBucketMetadataStep =
        new StoreControlledResourceMetadataStep(mockControlledResourceDao, mockDataReferenceDao);

    // Stub the flight map as of this step
    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(JobMapKeys.REQUEST.getKeyName(), ControlledResourceFixtures.BUCKET_RESOURCE);
    inputFlightMap.put(
        WorkspaceFlightMapKeys.WORKSPACE_ID, ControlledResourceFixtures.WORKSPACE_ID);
    inputFlightMap.put(ControlledResourceKeys.RESOURCE_ID, ControlledResourceFixtures.RESOURCE_ID);
    inputFlightMap.put(DataReferenceFlightMapKeys.REFERENCE_ID, ControlledResourceFixtures.DATA_REFERENCE_ID);
    inputFlightMap.put(ControlledResourceKeys.OWNER_EMAIL, ControlledResourceFixtures.OWNER_EMAIL);
    inputFlightMap.makeImmutable();

    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();
  }

  @Test
  public void testEntersInfo() throws InterruptedException, RetryException {
    final StepResult result = storeGoogleBucketMetadataStep.doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
    verify(mockControlledResourceDao).createControlledResource(dbModelArgumentCaptor.capture());
    verify(mockDataReferenceDao).createDataReference(dataReferenceRequestCaptor.capture(), ControlledResourceFixtures.DATA_REFERENCE_ID);

    final ControlledResourceDbModel metadata = dbModelArgumentCaptor.getValue();
    assertThat(metadata.getWorkspaceId(), equalTo(ControlledResourceFixtures.WORKSPACE_ID));
    assertThat(metadata.getResourceId(), equalTo(ControlledResourceFixtures.RESOURCE_ID));
    assertThat(metadata.getOwner().get(), equalTo(ControlledResourceFixtures.OWNER_EMAIL));
    assertThat(metadata.getAttributes(), equalTo("{\"bucketName\":\"my-bucket\"}"));

//    final DataReferenceRequest
  }
}
