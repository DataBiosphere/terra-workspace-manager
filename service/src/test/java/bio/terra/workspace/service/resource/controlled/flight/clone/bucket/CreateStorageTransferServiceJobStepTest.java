package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CreateStorageTransferServiceJobStep.ENABLED_STATUS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CreateStorageTransferServiceJobStep.TRANSFER_JOB_DESCRIPTION;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.CONTROL_PLANE_PROJECT_ID;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_CLONE_INPUTS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_CLONE_INPUTS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.STORAGE_TRANSFER_SERVICE_SA_EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class CreateStorageTransferServiceJobStepTest extends BaseUnitTest {

  public static final String FLIGHT_ID = "asdf-jkl";
  @Mock Storagetransfer mockStoragetransfer;
  @Mock Storagetransfer.TransferJobs mockTransferJobs;
  @Mock Storagetransfer.TransferJobs.Get mockTransferJobsGet;
  @Mock Storagetransfer.TransferJobs.Create mockTransferJobsCreate;
  private CreateStorageTransferServiceJobStep createStorageTransferServiceJobStep;

  @BeforeEach
  public void setup() throws IOException {
    createStorageTransferServiceJobStep =
        new CreateStorageTransferServiceJobStep(mockStoragetransfer);
    doReturn(mockTransferJobs).when(mockStoragetransfer).transferJobs();
    doReturn(mockTransferJobsGet).when(mockTransferJobs).get(anyString(), anyString());
    doReturn(mockTransferJobsCreate).when(mockTransferJobs).create(any(TransferJob.class));
  }

  @Test
  public void testDoStep() throws InterruptedException, RetryException, IOException {
    FlightMap workingMap = new FlightMap();
    workingMap.put(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.COPY_RESOURCE);
    workingMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, SOURCE_BUCKET_CLONE_INPUTS);
    workingMap.put(
        ControlledResourceKeys.DESTINATION_CLONE_INPUTS, DESTINATION_BUCKET_CLONE_INPUTS);
    workingMap.put(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, CONTROL_PLANE_PROJECT_ID);
    workingMap.put(
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL,
        STORAGE_TRANSFER_SERVICE_SA_EMAIL);

    FlightContext mockFlightContext = mock(FlightContext.class);
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();
    doReturn(FLIGHT_ID).when(mockFlightContext).getFlightId();

    final StepResult stepResult = createStorageTransferServiceJobStep.doStep(mockFlightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, stepResult.getStepStatus());
    verify(mockTransferJobs).create(any(TransferJob.class));
    verify(mockTransferJobsGet).execute();
    ArgumentCaptor<TransferJob> createArgumentCaptor = ArgumentCaptor.forClass(TransferJob.class);
    verify(mockTransferJobs).create(createArgumentCaptor.capture());
    TransferJob submittedJob = createArgumentCaptor.getValue();
    assertEquals("transferJobs/wsm-" + FLIGHT_ID, submittedJob.getName());
    assertEquals(TRANSFER_JOB_DESCRIPTION, submittedJob.getDescription());
    assertEquals(CONTROL_PLANE_PROJECT_ID, submittedJob.getProjectId());
    assertEquals(
        SOURCE_BUCKET_NAME, submittedJob.getTransferSpec().getGcsDataSource().getBucketName());
    assertEquals(
        DESTINATION_BUCKET_NAME, submittedJob.getTransferSpec().getGcsDataSink().getBucketName());
    assertFalse(
        submittedJob
            .getTransferSpec()
            .getTransferOptions()
            .getDeleteObjectsFromSourceAfterTransfer());
    assertFalse(
        submittedJob
            .getTransferSpec()
            .getTransferOptions()
            .getOverwriteObjectsAlreadyExistingInSink());
    assertEquals(ENABLED_STATUS, submittedJob.getStatus());
    verify(mockTransferJobsCreate).execute();
  }
}
