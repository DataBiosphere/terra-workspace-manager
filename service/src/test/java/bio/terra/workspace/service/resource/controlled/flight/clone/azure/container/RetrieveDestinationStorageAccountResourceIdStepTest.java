package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.exception.InvalidStorageAccountException;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;

public class RetrieveDestinationStorageAccountResourceIdStepTest extends BaseUnitTest {

  @Mock private ResourceDao resourceDao;
  @Mock private LandingZoneApiDispatch lzApiDispatch;
  @Mock private FlightContext flightContext;
  private FlightMap workingMap;
  private UUID workspaceId;

  private final AuthenticatedUserRequest testUser =
      new AuthenticatedUserRequest()
          .subjectId("fake-sub")
          .email("fake@ecample.com")
          .token(Optional.of("fake-token"));

  @BeforeEach
  void setup() {
    workspaceId = UUID.randomUUID();
    flightContext = mock(FlightContext.class);

    workingMap = new FlightMap();
    FlightMap inputParams = new FlightMap();
    inputParams.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId);
    var cloudContext = new AzureCloudContext("fake-tenant", "fake-sub", "fake-mrg");
    workingMap.put(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, cloudContext);

    doReturn(workingMap).when(flightContext).getWorkingMap();
    doReturn(inputParams).when(flightContext).getInputParameters();
    doReturn("fake-flight-id").when(flightContext).getFlightId();
  }

  @Test
  void doStep_failsIfNoLzNoSourceStorageAccount() throws InterruptedException {
    when(lzApiDispatch.getLandingZoneId(ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("not present"));

    RetrieveDestinationStorageAccountResourceIdStep step =
        new RetrieveDestinationStorageAccountResourceIdStep(resourceDao, lzApiDispatch, testUser);

    var result = step.doStep(flightContext);
    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_FAILURE_FATAL);
    assertEquals(result.getException().get().getClass(), LandingZoneNotFoundException.class);
  }

  @Test
  void doStep_failsIfHasLzNoStorageAccount() throws InterruptedException {
    when(lzApiDispatch.getLandingZoneId(ArgumentMatchers.any())).thenReturn(UUID.randomUUID());

    RetrieveDestinationStorageAccountResourceIdStep step =
        new RetrieveDestinationStorageAccountResourceIdStep(resourceDao, lzApiDispatch, testUser);

    var result = step.doStep(flightContext);
    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_FAILURE_FATAL);
    assertEquals(result.getException().get().getClass(), InvalidStorageAccountException.class);
  }

  @Test
  void doStep_retrieveStorageAccountFromLandingZone() throws InterruptedException {
    var storageAccountResourceId = UUID.randomUUID();
    var lzId = UUID.randomUUID();
    var lzStorageAcct = mock(ApiAzureLandingZoneDeployedResource.class);
    when(lzStorageAcct.getResourceId()).thenReturn(storageAccountResourceId.toString());
    when(lzApiDispatch.getLandingZoneId(ArgumentMatchers.any())).thenReturn(lzId);
    when(lzApiDispatch.getSharedStorageAccount(ArgumentMatchers.any(), ArgumentMatchers.eq(lzId)))
        .thenReturn(Optional.of(lzStorageAcct));

    RetrieveDestinationStorageAccountResourceIdStep step =
        new RetrieveDestinationStorageAccountResourceIdStep(resourceDao, lzApiDispatch, testUser);

    var result = step.doStep(flightContext);
    assertEquals(result.getStepStatus(), StepStatus.STEP_RESULT_SUCCESS);
    assertEquals(
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
            UUID.class),
        storageAccountResourceId);
  }

  @Test
  void doStep_retrieveStorageAccountFromDestWorkspace() throws InterruptedException {
    var storageAccountResourceId = UUID.randomUUID();
    List<WsmResource> accts =
        List.of(
            ControlledAzureStorageResource.builder()
                .common(
                    ControlledResourceFields.builder()
                        .resourceId(storageAccountResourceId)
                        .workspaceUuid(workspaceId)
                        .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                        .properties(Map.of())
                        .name(UUID.randomUUID().toString())
                        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                        .managedBy(ManagedByType.MANAGED_BY_USER)
                        .build())
                .storageAccountName("fakeacct")
                .region("eastus")
                .build());
    when(resourceDao.enumerateResources(
            ArgumentMatchers.eq(workspaceId),
            ArgumentMatchers.eq(WsmResourceFamily.AZURE_STORAGE_ACCOUNT),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.anyInt()))
        .thenReturn(accts);
    RetrieveDestinationStorageAccountResourceIdStep step =
        new RetrieveDestinationStorageAccountResourceIdStep(resourceDao, lzApiDispatch, testUser);

    var result = step.doStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    assertEquals(
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_STORAGE_ACCOUNT_RESOURCE_ID,
            UUID.class),
        storageAccountResourceId);
  }
}
