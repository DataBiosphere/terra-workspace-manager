package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bio.terra.stairway.*;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.database.CloneControlledAzureDatabaseResourceFlight;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class LaunchCloneControlledAzureDatabaseResourceFlightStepTest
    extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightBeanBag mockFlightBeanBag;
  @Mock private Stairway mockStairway;
  @Mock private AuthenticatedUserRequest userRequest;

  UUID destinationResourceId = UUID.randomUUID();
  UUID destinationWorkspaceId = UUID.randomUUID();
  UUID subFlightId = UUID.randomUUID();
  ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters("idworkflows_app", false);
  ControlledAzureDatabaseResource azureDatabaseResource =
      ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
              creationParameters, UUID.randomUUID(), CloningInstructions.COPY_RESOURCE)
          .build();

  @Test
  public void testDoSuccessWithCorrectFlightParams() throws InterruptedException {
    var step =
        new LaunchCloneControlledAzureDatabaseResourceFlightStep(
            azureDatabaseResource, subFlightId.toString(), destinationResourceId);

    var expectedInputs = new FlightMap();
    expectedInputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    expectedInputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    expectedInputs.put(
        WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
        CloningInstructions.COPY_RESOURCE);
    expectedInputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, azureDatabaseResource);
    expectedInputs.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format(
            "Clone Azure Controlled Database %s",
            azureDatabaseResource.getResourceId().toString()));
    expectedInputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    expectedInputs.put(WorkspaceFlightMapKeys.MERGE_POLICIES, false);

    mockWorkingMap = new FlightMap();
    FlightMap mockInputParams = new FlightMap();
    mockInputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    mockInputParams.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);

    when(mockFlightContext.getInputParameters()).thenReturn(mockInputParams);
    when(mockFlightContext.getStairway()).thenReturn(mockStairway);

    var result = step.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    verify(mockStairway)
        .submit(
            eq(subFlightId.toString()),
            eq(CloneControlledAzureDatabaseResourceFlight.class),
            argThat(inputs -> inputs.getMap().equals(expectedInputs.getMap())));
  }
}
