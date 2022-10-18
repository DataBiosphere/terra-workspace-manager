package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BQ_DATASET_UPDATE_PARAMETERS_NEW;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BQ_DATASET_UPDATE_PARAMETERS_PREV;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.bigquery.model.Dataset;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class UpdateBigQueryDatasetStepTest extends BaseUnitTest {
  private static final String PROJECT_ID = "my-gcp-project";

  private UpdateBigQueryDatasetStep updateBigQueryDatasetStep;

  @Mock private CrlService mockCrlService;
  @Mock private FlightContext mockFlightContext;
  @Mock private BigQueryCow mockBigQueryCow;
  @Mock private GcpCloudContextService mockGcpCloudContextService;
  Dataset mockExistingDataset = new Dataset();

  @Captor private ArgumentCaptor<Dataset> datasetCaptor;

  @BeforeEach
  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
      justification =
          "OK to ignore return value for Mockito functions that setup mocked return values")
  public void setup() throws IOException {
    // new dataset properties
    final FlightMap inputParameters = new FlightMap();
    inputParameters.put(ControlledResourceKeys.UPDATE_PARAMETERS, BQ_DATASET_UPDATE_PARAMETERS_NEW);
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    // previous dataset properties
    final FlightMap workingMap = new FlightMap();
    workingMap.put(
        ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS, BQ_DATASET_UPDATE_PARAMETERS_PREV);
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();

    doReturn(mockBigQueryCow).when(mockCrlService).createWsmSaBigQueryCow();
    doReturn(mockExistingDataset)
        .when(mockCrlService)
        .getBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class));
    doNothing()
        .when(mockCrlService)
        .updateBigQueryDataset(
            eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class), datasetCaptor.capture());

    final ControlledBigQueryDatasetResource datasetResource =
        ControlledResourceFixtures.makeDefaultControlledBqDatasetBuilder(null).build();

    doReturn(PROJECT_ID)
        .when(mockGcpCloudContextService)
        .getRequiredGcpProject(datasetResource.getWorkspaceId());

    updateBigQueryDatasetStep =
        new UpdateBigQueryDatasetStep(datasetResource, mockCrlService, mockGcpCloudContextService);
  }

  @Test
  public void testDoStepWithChange() throws InterruptedException, RetryException, IOException {
    // pretend the existing dataset has the previous values (so it will do the update)
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_PREV);

    // run the doStep and make sure it succeeds
    final StepResult result = updateBigQueryDatasetStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), result);

    // get() and update() should each have been called once
    verifyGetUpdateCalled(1, 1);

    // the Dataset argument to update() should match the NEW expiration times
    checkUpdateArgProperties(3600, 3601);
  }

  @Test
  public void testUndoStepWithChange() throws InterruptedException, IOException {
    // pretend the existing dataset has the new values (so it will do the update)
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_NEW);

    // run the undoStep and make sure it succeeds
    final StepResult result = updateBigQueryDatasetStep.undoStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), result);

    // get() and update() should each have been called once
    verifyGetUpdateCalled(1, 1);

    // the Dataset argument to update() should match the PREV expiration times
    checkUpdateArgProperties(4800, 4801);
  }

  @Test
  public void testDoStepWithoutChange() throws RetryException, InterruptedException, IOException {
    // pretend the existing dataset already has the updated values (so it won't do the update)
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_NEW);

    // run the doStep and make sure it succeeds
    final StepResult result = updateBigQueryDatasetStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), result);

    // get() should have been called once, update() not at all (because there was no change)
    verifyGetUpdateCalled(1, 0);
  }

  @Test
  public void testUndoStepWithoutChange() throws InterruptedException, IOException {
    // pretend the existing dataset has the previous values (so it won't do the update)
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_PREV);

    // run the undoStep and make sure it succeeds
    final StepResult result = updateBigQueryDatasetStep.undoStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), result);

    // get() should have been called once, update() not at all (because there was no change)
    verifyGetUpdateCalled(1, 0);
  }

  /**
   * Setup the mock return value for fetching the dataset to match the given table/partition
   * expiration times.
   */
  private void mockExistingDatasetExpirationTimes(ApiGcpBigQueryDatasetUpdateParameters params) {
    mockExistingDataset
        .setDefaultTableExpirationMs(
            BigQueryApiConversions.toBqExpirationTime(params.getDefaultTableLifetime()))
        .setDefaultPartitionExpirationMs(
            BigQueryApiConversions.toBqExpirationTime(params.getDefaultPartitionLifetime()));
  }

  /** Assert that the step called BigQuery get() and update() the specified number of times. */
  private void verifyGetUpdateCalled(int numTimesGetCalled, int numTimesUpdateCalled)
      throws IOException {
    verify(mockCrlService, times(numTimesGetCalled))
        .getBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class));
    verify(mockCrlService, times(numTimesUpdateCalled))
        .updateBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class), any());
  }

  /**
   * Assert that the table/partition expiration time properties of the Dataset argument to update()
   * match those given.
   */
  private void checkUpdateArgProperties(
      Integer defaultTableExpirationSec, Integer defaultPartitionExpirationSec) {
    final Long defaultTableExpirationMS = datasetCaptor.getValue().getDefaultTableExpirationMs();
    assertEquals(defaultTableExpirationSec * 1000, defaultTableExpirationMS);

    final Long defaultPartitionExpirationMS =
        datasetCaptor.getValue().getDefaultPartitionExpirationMs();
    assertEquals(defaultPartitionExpirationSec * 1000, defaultPartitionExpirationMS);
  }
}
