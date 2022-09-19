package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.Disks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;


public class GetAzureDiskStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private ComputeManager mockComputeManager;
  @Mock private Disks mockDisks;
  @Mock private Disk mockDisk;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  @BeforeEach
  public void setup() {
    // PublicIpAddresses mocks
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getComputeManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockComputeManager);
    when(mockComputeManager.disks()).thenReturn(mockDisks);
    when(mockException.getValue())
        .thenReturn(new ManagementError("ResourceNotFound", "Resource was not found."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }

  @Test
  public void getDisk_doesNotExist() throws InterruptedException {
    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    GetAzureDiskStep step =
        new GetAzureDiskStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureDisk(
                creationParameters.getName(),
                creationParameters.getRegion(),
                creationParameters.getSize()));

    when(mockDisks.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName()))
        .thenThrow(mockException);

    final StepResult stepResult = step.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getDisk_alreadyExists() throws InterruptedException {
    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    GetAzureDiskStep step =
        new GetAzureDiskStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureDisk(
                creationParameters.getName(),
                creationParameters.getRegion(),
                creationParameters.getSize()));

    when(mockDisks.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName()))
        .thenReturn(mockDisk);

    final StepResult stepResult = step.doStep(mockFlightContext);

    // Verify step returns error
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
