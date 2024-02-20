package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateDiskRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.Disks;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class CreateAzureDiskStepTest extends BaseSpringBootAzureUnitTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private ComputeManager mockComputeManager;
  @Mock private Disks mockDisks;
  @Mock private Disk mockDisk;
  @Mock private Disk.DefinitionStages.Blank mockDiskStage1;
  @Mock private Disk.DefinitionStages.WithGroup mockDiskStage2;
  @Mock private Disk.DefinitionStages.WithDiskSource mockDiskStage3;
  @Mock private Disk.DefinitionStages.WithDataDiskSource mockDiskStage4;
  @Mock private Disk.DefinitionStages.WithCreate mockDiskStage5;
  @Mock private Disk.DefinitionStages.WithCreate mockDiskStage6;
  @Mock private Disk.DefinitionStages.WithCreate mockDiskStage7;
  @Mock private ManagementException mockException;
  @Mock private FlightMap mockWorkingMap;

  private final ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    // PublicIpAddresses mocks
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(STUB_STRING_RETURN);
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getComputeManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockComputeManager);
    when(mockComputeManager.disks()).thenReturn(mockDisks);

    // Creation stages mocks
    when(mockDisks.define(anyString())).thenReturn(mockDiskStage1);
    when(mockDiskStage1.withRegion(anyString())).thenReturn(mockDiskStage2);
    when(mockDiskStage2.withExistingResourceGroup(anyString())).thenReturn(mockDiskStage3);
    when(mockDiskStage3.withData()).thenReturn(mockDiskStage4);
    when(mockDiskStage4.withSizeInGB(anyInt())).thenReturn(mockDiskStage5);
    when(mockDiskStage5.withTag(anyString(), anyString())).thenReturn(mockDiskStage6);
    when(mockDiskStage6.withTag(anyString(), anyString())).thenReturn(mockDiskStage7);
    when(mockDiskStage7.create(any(Context.class))).thenReturn(mockDisk);

    // Deletion mocks

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Resource already exists."));

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }

  @Test
  void createDisk() throws InterruptedException {
    ApiAzureDiskCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureDiskCreationParameters();

    var createAzureDiskStep =
        new CreateAzureDiskStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureDisk(
                creationParameters.getName(),
                DEFAULT_AZURE_RESOURCE_REGION,
                creationParameters.getSize()));

    StepResult stepResult = createAzureDiskStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockDiskStage7).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreateDiskRequestData> requestDataOpt =
        context.getValues().values().stream()
            .filter(CreateDiskRequestData.class::isInstance)
            .map(CreateDiskRequestData.class::cast)
            .findFirst();

    CreateDiskRequestData expected =
        CreateDiskRequestData.builder()
            .setName(creationParameters.getName())
            .setRegion(Region.fromName(DEFAULT_AZURE_RESOURCE_REGION))
            .setSize(50)
            .setTenantId(mockAzureCloudContext.getAzureTenantId())
            .setSubscriptionId(mockAzureCloudContext.getAzureSubscriptionId())
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(requestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createDisk_alreadyExists() throws InterruptedException {
    ApiAzureDiskCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureDiskCreationParameters();

    CreateAzureDiskStep createAzureDiskStep =
        new CreateAzureDiskStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureDisk(
                creationParameters.getName(),
                DEFAULT_AZURE_RESOURCE_REGION,
                creationParameters.getSize()));

    // Stub creation to throw Conflict exception.
    when(mockDiskStage7.create(any(Context.class))).thenThrow(mockException);

    StepResult stepResult = createAzureDiskStep.doStep(mockFlightContext);

    // Verify step still returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void deleteDisk() throws InterruptedException {
    ApiAzureDiskCreationParameters creationParameters =
        ControlledAzureResourceFixtures.getAzureDiskCreationParameters();

    CreateAzureDiskStep createAzureDiskStep =
        new CreateAzureDiskStep(
            mockAzureConfig,
            mockCrlService,
            ControlledAzureResourceFixtures.getAzureDisk(
                creationParameters.getName(),
                DEFAULT_AZURE_RESOURCE_REGION,
                creationParameters.getSize()));

    StepResult stepResult = createAzureDiskStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure deletion was called
    verify(mockDisks)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }
}
