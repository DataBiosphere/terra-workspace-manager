package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineExtension;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

class EnableVmLoggingStepTest extends BaseAzureUnitTest {

  /* these are all used in the constructor to EnableVmLoggingStep */
  @Mock private AzureConfiguration azureConfig;
  @Mock private CrlService crlService;
  @Mock private ControlledAzureVmResource resource;
  @Mock private LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock private SamService samService;
  @Mock private WorkspaceService mockWorkspaceService;

  @Mock private HttpResponse mockHttpResponse;

  @Mock private FlightContext flightContext;

  private BearerToken bearerToken;
  private VirtualMachine.Update updateMachineMock;

  @BeforeEach
  void setup() {
    flightContext = spy(FlightContext.class);

    var inputParameters = new FlightMap();
    when(flightContext.getInputParameters()).thenReturn(inputParameters);

    var vmId = "vmId";
    var workingMap = new FlightMap();
    var azureCloudContext = new AzureCloudContext(null, null);
    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, azureCloudContext);
    workingMap.put(AzureVmHelper.WORKING_MAP_VM_ID, vmId);
    when(flightContext.getWorkingMap()).thenReturn(workingMap);

    bearerToken = new BearerToken("wsm-token");

    var lzId = UUID.randomUUID();
    var workspaceId = UUID.randomUUID();
    when(resource.getWorkspaceId()).thenReturn(workspaceId);
    when(mockWorkspaceService.getWorkspace(workspaceId))
        .thenReturn(WorkspaceFixtures.buildMcWorkspace(workspaceId));
    when(landingZoneApiDispatch.getLandingZoneId(
            eq(bearerToken), argThat(a -> a.getWorkspaceId().equals(workspaceId))))
        .thenReturn(lzId);

    var response = new ApiAzureLandingZoneResourcesList();
    when(landingZoneApiDispatch.listAzureLandingZoneResourcesByPurpose(
            any(), eq(lzId), eq(ResourcePurpose.SHARED_RESOURCE)))
        .thenReturn(response);

    // used in #confirmBackoutOnFailure()
    Map<String, VirtualMachineExtension> extensionMap =
        Map.of("AzureMonitorLinuxAgent", mock(VirtualMachineExtension.class));
    var extensionSpy = spy(extensionMap);

    var mockVirtualMachine = mock(VirtualMachine.class);
    when(mockVirtualMachine.listExtensions()).thenReturn(extensionSpy);

    updateMachineMock = mock(VirtualMachine.Update.class);
    // verify #apply() is called on removing the extension
    when(updateMachineMock.withoutExtension(any())).thenReturn(updateMachineMock);
    when(mockVirtualMachine.update()).thenReturn(updateMachineMock);

    var mockVirtualMachines = mock(VirtualMachines.class);
    when(mockVirtualMachines.getById(vmId)).thenReturn(mockVirtualMachine);
    var mockComputeManager = mock(ComputeManager.class);
    when(mockComputeManager.virtualMachines()).thenReturn(mockVirtualMachines);
    when(crlService.getComputeManager(eq(azureCloudContext), eq(azureConfig)))
        .thenReturn(mockComputeManager);
  }

  @Test
  void confirmRetry() throws InterruptedException {
    // this forces the failure to trigger the maybeRetryStatus(...)
    when(samService.getWsmServiceAccountToken())
        .thenThrow(new ManagementException("FIRST TIME FAILURE", mockHttpResponse));

    var step =
        new EnableVmLoggingStep(
            azureConfig,
            crlService,
            resource,
            landingZoneApiDispatch,
            samService,
            mockWorkspaceService);
    try (MockedStatic mockedUtils = mockStatic(AzureManagementExceptionUtils.class)) {
      step.doStep(flightContext);
      mockedUtils.verify(
          () -> AzureManagementExceptionUtils.maybeRetryStatus(any(ManagementException.class)));
    }
  }

  @Test
  void confirmBackoutOnFailure() throws InterruptedException {

    when(samService.getWsmServiceAccountToken()).thenReturn(bearerToken.getToken());

    var step =
        new EnableVmLoggingStep(
            azureConfig,
            crlService,
            resource,
            landingZoneApiDispatch,
            samService,
            mockWorkspaceService) {
          @Override
          Optional<ApiAzureLandingZoneDeployedResource> getDataCollectionRuleFromLandingZone() {
            return Optional.of(mock(ApiAzureLandingZoneDeployedResource.class));
          }

          @Override
          void createExtension(FlightContext context, VirtualMachine virtualMachine) {
            // do nothing ...
          }

          @Override
          void createDataCollectionRuleAssociation(
              FlightContext context, ApiAzureLandingZoneDeployedResource dcr, String vmId) {
            // this has to do with creating the extension ...
            // and we aren't validating that here
          }
        };

    assertEquals(step.doStep(flightContext), StepResult.getStepResultSuccess());
    verify(updateMachineMock).withoutExtension("AzureMonitorLinuxAgent");
    verify(updateMachineMock).apply();
  }
}
