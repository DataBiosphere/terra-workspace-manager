package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.*;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class AwaitCloneControlledAzureDatabaseResourceFlightStepTest
    extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightBeanBag mockFlightBeanBag;
  @Mock private Stairway mockStairway;

  UUID subFlightId = UUID.randomUUID();
  ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters("idworkflows_app", false);
  ControlledAzureDatabaseResource azureDatabaseResource =
      ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
              creationParameters, UUID.randomUUID(), CloningInstructions.COPY_RESOURCE)
          .build();

  @Test
  public void testDoStepSuccessWithCorrectResultObject()
      throws InterruptedException, JsonProcessingException {
    var cloneFlightStep =
        new AwaitCloneControlledAzureDatabaseResourceFlightStep(
            azureDatabaseResource, subFlightId.toString());

    //        ControlledAzureDatabaseResource clonedDatabaseResource =
    // ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
    //                        creationParameters, UUID.randomUUID(),
    // CloningInstructions.COPY_RESOURCE)
    //                .build();

    var controlledResourceFields =
        ControlledResourceFields.builder()
            .workspaceUuid(UUID.randomUUID())
            .resourceId(UUID.randomUUID())
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .name(getAzureName("db"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .createdByEmail(DEFAULT_USER_EMAIL)
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .region(DEFAULT_AZURE_RESOURCE_REGION)
            .build();

    var clonedDatabaseResource =
        ControlledAzureDatabaseResource.builder()
            .common(controlledResourceFields)
            .databaseName(creationParameters.getName())
            .databaseOwner(creationParameters.getOwner())
            .allowAccessForAllWorkspaceUsers(creationParameters.isAllowAccessForAllWorkspaceUsers())
            .build();

    //        var controlledCloneResourceCommonFields = ControlledResourceFields.builder().
    //
    //        ControlledAzureDatabaseResource destinationDatabaseResource =
    //                ControlledAzureDatabaseResource.builder()
    //                        .databaseName(destinationDatabaseName)
    //                        .databaseOwner(destinationManagedIdentity.getName())
    //                        .common(
    //                                this.buildControlledCloneResourceCommonFields(
    //                                        destinationWorkspaceId,
    //                                        destinationResourceId,
    //                                        null,
    //                                        destinationResourceName,
    //                                        description,
    //
    // samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
    //                                        sourceDatabase.getRegion()))
    //                        .build();

    var clonedResource =
        new ClonedAzureResource(
            CloningInstructions.COPY_RESOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            clonedDatabaseResource);

    var resultFlightMap = new FlightMap();
    resultFlightMap.put(JobMapKeys.RESPONSE.getKeyName(), clonedResource);

    //        var abc = resultFlightMap.get(JobMapKeys.RESPONSE.getKeyName(),
    // ClonedAzureResource.class);

    var flightState = new FlightState();
    flightState.setFlightId(subFlightId.toString());
    flightState.setFlightStatus(FlightStatus.SUCCESS);
    flightState.setResultMap(resultFlightMap);

    when(mockFlightContext.getStairway()).thenReturn(mockStairway);
    when(mockStairway.waitForFlight(eq(subFlightId.toString()), any(), any()))
        .thenReturn(flightState);

    var result = cloneFlightStep.doStep(mockFlightContext);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }
}
