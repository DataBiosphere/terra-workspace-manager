package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.*;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class AwaitCloneControlledAzureDatabaseResourceFlightStepTest
    extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private Stairway mockStairway;
  @Mock private FlightMap mockResultFlightMap;

  UUID subFlightId = UUID.randomUUID();
  private final FlightMap workingMap = new FlightMap();
  ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters("idworkflows_app", false);
  ControlledAzureDatabaseResource azureDatabaseResource =
      ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
              creationParameters, UUID.randomUUID(), CloningInstructions.COPY_RESOURCE)
          .build();

  @Test
  public void testDoStepSuccessWithCorrectResultObject() throws InterruptedException {
    var cloneFlightStep =
        new AwaitCloneControlledAzureDatabaseResourceFlightStep(
            azureDatabaseResource, subFlightId.toString());

    ControlledAzureDatabaseResource clonedDatabaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, UUID.randomUUID(), CloningInstructions.COPY_RESOURCE)
            .build();
    var clonedResource =
        new ClonedAzureResource(
            CloningInstructions.COPY_RESOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            clonedDatabaseResource);

    var flightState = new FlightState();
    flightState.setFlightId(subFlightId.toString());
    flightState.setFlightStatus(FlightStatus.SUCCESS);
    flightState.setResultMap(mockResultFlightMap);

    when(mockFlightContext.getStairway()).thenReturn(mockStairway);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);
    when(mockStairway.waitForFlight(eq(subFlightId.toString()), any(), any()))
        .thenReturn(flightState);
    when(mockResultFlightMap.get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class))
        .thenReturn(clonedResource);

    var result = cloneFlightStep.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    var resultWorkingMap = mockFlightContext.getWorkingMap();
    var resourceIdToResult =
        resultWorkingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
            new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {});
    assert resourceIdToResult != null;
    var cloneDetails = resourceIdToResult.get(azureDatabaseResource.getResourceId());

    assertThat(cloneDetails.getResult(), equalTo(WsmCloneResourceResult.SUCCEEDED));
    assertThat(cloneDetails.getStewardshipType(), equalTo(StewardshipType.CONTROLLED));
    assertThat(cloneDetails.getResourceType(), equalTo(WsmResourceType.CONTROLLED_AZURE_DATABASE));
    assertThat(cloneDetails.getCloningInstructions(), equalTo(CloningInstructions.COPY_RESOURCE));
    assertThat(cloneDetails.getSourceResourceId(), equalTo(azureDatabaseResource.getResourceId()));
    assertThat(
        cloneDetails.getDestinationResourceId(), equalTo(clonedDatabaseResource.getResourceId()));
    assertThat(cloneDetails.getErrorMessage(), equalTo(null));
    assertThat(cloneDetails.getName(), equalTo(azureDatabaseResource.getName()));
    assertThat(cloneDetails.getDescription(), equalTo(azureDatabaseResource.getDescription()));
  }
}
