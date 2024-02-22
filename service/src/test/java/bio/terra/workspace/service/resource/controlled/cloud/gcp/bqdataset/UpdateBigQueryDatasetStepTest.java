package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BQ_DATASET_UPDATE_PARAMETERS_NEW;
import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.BQ_DATASET_UPDATE_PARAMETERS_PREV;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class UpdateBigQueryDatasetStepTest extends BaseSpringBootUnitTest {
  private static final String PROJECT_ID = "my-gcp-project";
  private static final String DATASET_ID = "my-ds-id";

  @Mock private CrlService mockCrlService;
  @Mock private FlightContext mockFlightContext;
  @Mock private BigQueryCow mockBigQueryCow;
  @Mock private GcpCloudContextService mockGcpCloudContextService;

  UpdateBigQueryDatasetStep updateBigQueryDatasetStep;
  final Dataset mockExistingDataset = new Dataset();
  final ControlledBigQueryDatasetResource baseDatasetResource =
      ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(null)
          .projectId(PROJECT_ID)
          .build();
  final FlightMap workingMap = new FlightMap();
  DbUpdater dbUpdater;

  @Captor private ArgumentCaptor<Dataset> datasetCaptor;

  @BeforeEach
  public void setup() throws IOException {
    // new dataset properties
    FlightMap inputParameters = new FlightMap();
    inputParameters.put(
        WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS, BQ_DATASET_UPDATE_PARAMETERS_NEW);
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);
    when(mockCrlService.createWsmSaBigQueryCow()).thenReturn(mockBigQueryCow);
    doNothing()
        .when(mockCrlService)
        .updateBigQueryDataset(
            eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class), datasetCaptor.capture());

    when(mockGcpCloudContextService.getRequiredGcpProject(baseDatasetResource.getWorkspaceId()))
        .thenReturn(PROJECT_ID);

    updateBigQueryDatasetStep = new UpdateBigQueryDatasetStep(mockCrlService);
  }

  @Test
  public void testDoStepWithChange() throws InterruptedException, RetryException, IOException {
    // pretend the existing dataset has the previous values (so it will do the update)
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_PREV);

    // run the doStep and make sure it succeeds
    try (MockedStatic<CrlService> crlServiceMockedStatic = Mockito.mockStatic(CrlService.class)) {
      crlServiceMockedStatic
          .when(
              () ->
                  CrlService.getBigQueryDataset(
                      eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class)))
          .thenReturn(mockExistingDataset);
      StepResult result = updateBigQueryDatasetStep.doStep(mockFlightContext);
      assertEquals(StepResult.getStepResultSuccess(), result);
      crlServiceMockedStatic.verify(
          () ->
              CrlService.getBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class)),
          times(1));
    }

    // update() should each have been called once
    verifyUpdateCalled(1);

    // the Dataset argument to update() should match the NEW expiration times
    checkUpdateArgProperties(3600, 3601);
  }

  @Test
  public void testUndoStep() throws InterruptedException, IOException {
    // We always perform the update on undo, ensuring the original values are set
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_PREV);

    // run the undoStep and make sure it succeeds
    try (MockedStatic<CrlService> crlServiceMockedStatic = Mockito.mockStatic(CrlService.class)) {
      crlServiceMockedStatic
          .when(
              () ->
                  CrlService.getBigQueryDataset(
                      eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class)))
          .thenReturn(mockExistingDataset);
      StepResult result = updateBigQueryDatasetStep.undoStep(mockFlightContext);
      assertEquals(StepResult.getStepResultSuccess(), result);
      crlServiceMockedStatic.verify(
          () ->
              CrlService.getBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class)),
          times(1));
    }

    // get() and update() should each have been called once
    verifyUpdateCalled(1);

    // the Dataset argument to update() should match the PREV expiration times
    checkUpdateArgProperties(4800, 4801);
  }

  @Test
  public void testDoStepWithoutChange() throws RetryException, InterruptedException, IOException {
    // pretend the existing dataset already has the updated values (so it won't do the update)
    mockExistingDatasetExpirationTimes(BQ_DATASET_UPDATE_PARAMETERS_NEW);

    // run the doStep and make sure it succeeds
    try (MockedStatic<CrlService> crlServiceMockedStatic = Mockito.mockStatic(CrlService.class)) {
      crlServiceMockedStatic
          .when(
              () ->
                  CrlService.getBigQueryDataset(
                      eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class)))
          .thenReturn(mockExistingDataset);
      StepResult result = updateBigQueryDatasetStep.doStep(mockFlightContext);
      assertEquals(StepResult.getStepResultSuccess(), result);
      crlServiceMockedStatic.verify(
          () ->
              CrlService.getBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class)),
          times(0));
    }

    // If there are not changes neither get() nor update() should be called
    verifyUpdateCalled(0);
  }

  /**
   * Setup the mock return value for fetching the dataset to match the given table/partition
   * expiration times.
   */
  private void mockExistingDatasetExpirationTimes(ApiGcpBigQueryDatasetUpdateParameters params) {
    Long tableLifetime = params.getDefaultTableLifetime();
    Long partitionLifetime = params.getDefaultPartitionLifetime();

    // Construct a new updater with the right initial settings for this test.
    String jsonAttributes =
        DbSerDes.toJson(
            new ControlledBigQueryDatasetAttributes(
                DATASET_ID, PROJECT_ID, tableLifetime, partitionLifetime));
    var originalData =
        new DbUpdater.UpdateData()
            .setName(baseDatasetResource.getName())
            .setDescription(baseDatasetResource.getDescription())
            .setCloningInstructions(CloningInstructions.COPY_NOTHING)
            .setJsonAttributes(jsonAttributes);
    dbUpdater = new DbUpdater(originalData, new DbUpdater.UpdateData());
    workingMap.put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);

    mockExistingDataset
        .setDefaultTableExpirationMs(BigQueryApiConversions.toBqExpirationTime(tableLifetime))
        .setDefaultPartitionExpirationMs(
            BigQueryApiConversions.toBqExpirationTime(partitionLifetime));
  }

  /** Assert that the step called BigQuery get() and update() the specified number of times. */
  private void verifyUpdateCalled(int numTimesUpdateCalled) throws IOException {
    verify(mockCrlService, times(numTimesUpdateCalled))
        .updateBigQueryDataset(eq(mockBigQueryCow), eq(PROJECT_ID), any(String.class), any());
  }

  /**
   * Assert that the table/partition expiration time properties of the Dataset argument to update()
   * match those given.
   */
  private void checkUpdateArgProperties(
      Integer defaultTableExpirationSec, Integer defaultPartitionExpirationSec) {
    Long defaultTableExpirationMS = datasetCaptor.getValue().getDefaultTableExpirationMs();
    assertEquals(defaultTableExpirationSec * 1000, defaultTableExpirationMS);

    Long defaultPartitionExpirationMS = datasetCaptor.getValue().getDefaultPartitionExpirationMs();
    assertEquals(defaultPartitionExpirationSec * 1000, defaultPartitionExpirationMS);
  }
}
