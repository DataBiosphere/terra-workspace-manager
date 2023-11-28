package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class CreateAzureStorageContainerStepTest extends BaseMockitoStrictStubbingTest {

  @Mock private ControlledResourceService mockControlledResourceService;
  @Mock private ControlledResource mockControlledResource;
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightMap mockInputParameters;
  private final String storageContainerName = "sc-name";
  private final UUID workspaceId = UUID.randomUUID();
  private final UUID destinationContainerId = UUID.randomUUID();
  private final AuthenticatedUserRequest mockUserRequest =
      new AuthenticatedUserRequest().email("lol@foo.com");
  private final UUID mockDestinationWorkspaceId = UUID.randomUUID();
  private final ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, false);
  private final ControlledAzureDatabaseResource databaseResource =
      ControlledAzureResourceFixtures.makePrivateControlledAzureDatabaseResourceBuilder(
              creationParameters, workspaceId, null)
          .build();

  @Test
  void testSuccess() throws InterruptedException {
    var step = setupStepTest();

    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    verify(mockFlightContext.getWorkingMap())
        .put(
            eq(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_STORAGE_CONTAINER),
            argThat((ControlledResource resource) -> resource != null));
  }

  @NotNull
  private CreateAzureStorageContainerStep setupStepTest() {
    createMockFlightContext();

    return new CreateAzureStorageContainerStep(
        storageContainerName, destinationContainerId, mockControlledResourceService);
  }

  private FlightContext createMockFlightContext() {
    when(mockInputParameters.get(
            JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class))
        .thenReturn(mockUserRequest);
    when(mockInputParameters.get(
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, ControlledAzureDatabaseResource.class))
        .thenReturn(databaseResource);
    when(mockInputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class))
        .thenReturn(mockDestinationWorkspaceId);
    when(mockControlledResourceService.createControlledResourceSync(
            any(ControlledAzureStorageContainerResource.class), any(), eq(mockUserRequest), any()))
        .thenReturn(mockControlledResource);
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockFlightContext.getInputParameters()).thenReturn(mockInputParameters);

    return mockFlightContext;
  }
}
