package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.BlobCopier;
import bio.terra.workspace.service.resource.controlled.cloud.azure.BlobCopierResult;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.util.polling.LongRunningOperationStatus;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class CopyAzureStorageContainerBlobsStepUnitTest extends BaseAzureSpringBootUnitTest {

  @Mock private AzureStorageAccessService azureStorageAccessService;
  @Mock private ResourceDao resourceDao;
  @Mock private ControlledAzureStorageContainerResource sourceContainer;
  @Mock private FlightContext flightContext;

  private final List<String> clonePrefixes = new ArrayList<>(List.of("analyses/"));

  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest().email("example@example.com").token(Optional.of("fake-token"));

  @BeforeEach
  void setup() {
    var destinationWorkspaceId = UUID.randomUUID();

    var inputParameters = new FlightMap();
    inputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    inputParameters.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.PREFIXES_TO_CLONE, clonePrefixes);

    var destinationContainer =
        ControlledAzureResourceFixtures.getAzureStorageContainer("sc-" + UUID.randomUUID());
    var workingMap = new FlightMap();

    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
        destinationContainer);

    when(flightContext.getInputParameters()).thenReturn(inputParameters);
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
  }

  @Test
  void copyBlobs_success() throws InterruptedException {
    var copier = mock(BlobCopier.class);
    var copyBlobsStep =
        new CopyAzureStorageContainerBlobsStep(
            azureStorageAccessService, sourceContainer, resourceDao, userRequest, copier);
    var copyResult =
        new BlobCopierResult(Map.of(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED, List.of()));
    when(copier.copyBlobs(any(), any(), eq(clonePrefixes))).thenReturn(copyResult);

    var result = copyBlobsStep.doStep(flightContext);

    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_SUCCESS);
  }

  @Test
  void copyBlobs_failsWhenBlobCopyFails() throws InterruptedException {
    var copier = mock(BlobCopier.class);
    var copyBlobsStep =
        new CopyAzureStorageContainerBlobsStep(
            azureStorageAccessService, sourceContainer, resourceDao, userRequest, copier);
    var errorCopyResult =
        new BlobCopierResult(
            Map.of(
                LongRunningOperationStatus.SUCCESSFULLY_COMPLETED,
                List.of(),
                LongRunningOperationStatus.FAILED,
                List.of()));
    when(copier.copyBlobs(any(), any(), eq(clonePrefixes))).thenReturn(errorCopyResult);

    var result = copyBlobsStep.doStep(flightContext);

    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
