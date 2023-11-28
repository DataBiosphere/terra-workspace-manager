package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("azureUnit")
public class CopyAzureControlledDatabaseDefinitionStepTest extends BaseAzureUnitTest {
  @MockBean private ResourceDao mockResourceDao;
  private UUID workspaceId;
  private FlightContext flightContext;
  private FlightMap workingMap;
  private FlightMap inputParams;
  private ControlledResourceService controlledResourceService;

  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest()
          .subjectId("fake-sub")
          .email("fake@ecample.com")
          .token(Optional.of("fake-token"));

  @BeforeEach
  void setup() {
    workspaceId = UUID.randomUUID();
    flightContext = mock(FlightContext.class);

    workingMap = new FlightMap();
    inputParams = new FlightMap();
    inputParams.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, workspaceId);

    controlledResourceService = getMockControlledResourceService();

    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    when(flightContext.getInputParameters()).thenReturn(inputParams);
    when(flightContext.getFlightId()).thenReturn("fake-flight-id");
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn("foo@gmail.com");
  }

  private void buildIdentityResource(
      String managedIdentityName, String resourceName, UUID resourceId, UUID workspaceId) {
    when(mockResourceDao.getResourceByName(workspaceId, resourceName))
        .thenReturn(
            ControlledAzureManagedIdentityResource.builder()
                .managedIdentityName(managedIdentityName)
                .common(
                    ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                        .resourceId(resourceId)
                        .workspaceUuid(workspaceId)
                        .cloningInstructions(CloningInstructions.COPY_DEFINITION)
                        .properties(Map.of())
                        .name(resourceName)
                        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                        .managedBy(ManagedByType.MANAGED_BY_USER)
                        .build())
                .build());
  }

  private ControlledAzureDatabaseResource buildDatabaseResource(
      String databaseName, String resourceName, UUID resourceId, UUID workspaceId) {
    return ControlledAzureDatabaseResource.builder()
        .databaseName(databaseName)
        .databaseOwner("idowner")
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .resourceId(resourceId)
                .workspaceUuid(workspaceId)
                .cloningInstructions(CloningInstructions.COPY_DEFINITION)
                .properties(Map.of())
                .name(resourceName)
                .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                .managedBy(ManagedByType.MANAGED_BY_USER)
                .build())
        .build();
  }

  @Test
  void doStep_clonesDefinition() throws InterruptedException {
    var resourceName = "dbappname";
    var identityResourceName = "id%s".formatted(UUID.randomUUID().toString());
    var sourceDatabaseName = "db%s".formatted(UUID.randomUUID().toString().replace('-', '_'));
    var destDatabaseName = "db%s".formatted(UUID.randomUUID().toString().replace('-', '_'));
    var destResourceId = UUID.randomUUID();
    var sourceWorkspaceId = UUID.randomUUID();

    inputParams.put(ControlledResourceKeys.DESTINATION_RESOURCE_ID, destResourceId);
    inputParams.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, resourceName);
    inputParams.put(ControlledResourceKeys.DESTINATION_DATABASE_NAME, destDatabaseName);
    inputParams.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    buildIdentityResource(identityResourceName, "idowner", UUID.randomUUID(), workspaceId);
    var sourceDatabase =
        buildDatabaseResource(
            sourceDatabaseName, resourceName, UUID.randomUUID(), sourceWorkspaceId);
    var createdDatabase =
        buildDatabaseResource(destDatabaseName, resourceName, destResourceId, workspaceId);

    when(controlledResourceService.createControlledResourceSync(
            any(), any(), eq(userRequest), any()))
        .thenReturn(createdDatabase);

    var step =
        new CopyControlledAzureDatabaseDefinitionStep(
            mockSamService(),
            userRequest,
            sourceDatabase,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION,
            mockResourceDao);

    var result = step.doStep(flightContext);
    var cloned =
        workingMap.get(
            ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
            ControlledAzureDatabaseResource.class);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    verify(controlledResourceService).createControlledResourceSync(any(), any(), any(), any());
    assertNotNull(cloned);
    assertEquals(destResourceId, cloned.getResourceId());
    assertEquals(destDatabaseName, cloned.getDatabaseName());
    assertEquals(resourceName, cloned.getName());
    assertEquals(CloningInstructions.COPY_DEFINITION, cloned.getCloningInstructions());
  }

  @Test
  void undoStep_deletesDatabase() throws InterruptedException {
    var clonedDatabase =
        buildDatabaseResource("dbfake_db", "fake-resource", UUID.randomUUID(), UUID.randomUUID());
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedDatabase);

    var step =
        new CopyControlledAzureDatabaseDefinitionStep(
            mockSamService(),
            userRequest,
            null,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION,
            mockResourceDao);

    var result = step.undoStep(flightContext);

    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
    verify(controlledResourceService).deleteControlledResourceSync(any(), any(), eq(false), any());
  }
}
