package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class CreateNamespaceRoleStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private FlightContext mockFlightContext;
  @Mock private AzureDatabaseUtilsRunner mockAzureDatabaseUtilsRunner;
  @Mock private ResourceDao mockResourceDao;

  @Test
  void testDoStepSuccess() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var dbCount = 3;
    var dbResources = createSharedDbResources(owner, workspaceId, false, dbCount);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, dbResources.stream().map(ControlledAzureDatabaseResource::getName).toList());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var principalId = UUID.randomUUID().toString();
    when(mockWorkingMap.get(GetManagedIdentityStep.MANAGED_IDENTITY_PRINCIPAL_ID, String.class))
        .thenReturn(principalId);
    dbResources.forEach(
        dbResource ->
            when(mockResourceDao.getResourceByName(workspaceId, dbResource.getName()))
                .thenReturn(dbResource));

    var step =
        spy(
            new CreateNamespaceRoleStep(
                workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao));

    var result = step.doStep(createMockFlightContext());
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    verify(mockAzureDatabaseUtilsRunner)
        .createNamespaceRole(
            mockAzureCloudContext,
            workspaceId,
            "create-namespace-role-" + resource.getResourceId(),
            resource.getKubernetesServiceAccount(),
            principalId,
            dbResources.stream()
                .map(ControlledAzureDatabaseResource::getDatabaseName)
                .collect(Collectors.toSet()));

    dbResources.forEach(
        dbResource ->
            verify(step)
                .validateDatabaseAccess(
                    argThat(r -> r.resourceName().equals(dbResource.getName()))));
  }

  @Test
  void testUndoStepSuccess() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var result = step.undoStep(createMockFlightContext());
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    verify(mockAzureDatabaseUtilsRunner)
        .deleteNamespaceRole(
            mockAzureCloudContext,
            workspaceId,
            "undo-create-namespace-role-" + resource.getResourceId(),
            resource.getKubernetesServiceAccount());
  }

  @Test
  void testGetDatabaseResourceSuccess() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var dbResource = createSharedDbResources(owner, workspaceId, false, 1).get(0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockResourceDao.getResourceByName(workspaceId, dbResource.getName()))
        .thenReturn(dbResource);

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var result = step.getDatabaseResource(dbResource.getName());
    assertThat(result.resourceName(), equalTo(dbResource.getName()));
    assertThat(result.resource(), equalTo(Optional.of(dbResource)));
    assertThat(result.errorMessage(), equalTo(Optional.empty()));
  }

  @Test
  void testGetDatabaseResourceDoesNotExist() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var dbResources = createSharedDbResources(owner, workspaceId, false, 1);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, dbResources.stream().map(ControlledAzureDatabaseResource::getName).toList());

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();
    dbResources.forEach(
        dbResource ->
            when(mockResourceDao.getResourceByName(workspaceId, dbResource.getName()))
                .thenThrow(new ResourceNotFoundException("not found")));

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var databaseResource = dbResources.get(0);
    var result = step.getDatabaseResource(databaseResource.getName());
    assertThat(result.resourceName(), equalTo(databaseResource.getName()));
    assertThat(result.resource(), equalTo(Optional.empty()));
    assertThat(result.errorMessage().isPresent(), equalTo(true));
  }

  @Test
  void testGetDatabaseResourceNotADatabase() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var notADatabase = ControlledAzureResourceFixtures.getAzureDisk("notadatabase", "us", 0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of(notADatabase.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockResourceDao.getResourceByName(workspaceId, notADatabase.getName()))
        .thenReturn(notADatabase);

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var result = step.getDatabaseResource(notADatabase.getName());
    assertThat(result.resourceName(), equalTo(notADatabase.getName()));
    assertThat(result.resource(), equalTo(Optional.empty()));
    assertThat(result.errorMessage().isPresent(), equalTo(true));
  }

  @Test
  void testValidateDatabaseAccessShared() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var dbResource = createSharedDbResources(owner, workspaceId, false, 1).get(0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var resolution =
        new DatabaseResolution(dbResource.getName(), Optional.of(dbResource), Optional.empty());
    var result = step.validateDatabaseAccess(resolution);
    assertThat(result, equalTo(resolution));
  }

  @Test
  void testValidateDatabaseAccessSharedButDifferentOwner() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var differentOwner = UUID.randomUUID().toString();
    var dbResource = createSharedDbResources(owner, workspaceId, false, 1).get(0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            differentOwner, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var resolution =
        new DatabaseResolution(dbResource.getName(), Optional.of(dbResource), Optional.empty());
    var result = step.validateDatabaseAccess(resolution);
    assertThat(result.errorMessage().isPresent(), equalTo(true));
  }

  @Test
  void testValidateDatabaseAccessSharedDifferentOwnerAllowed() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var differentOwner = UUID.randomUUID().toString();
    var dbResource = createSharedDbResources(owner, workspaceId, true, 1).get(0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            differentOwner, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var resolution =
        new DatabaseResolution(dbResource.getName(), Optional.of(dbResource), Optional.empty());
    var result = step.validateDatabaseAccess(resolution);
    assertThat(result, equalTo(resolution));
  }

  @Test
  void testValidateDatabaseAccessPrivate() {
    var workspaceId = UUID.randomUUID();
    var assignedUser = UUID.randomUUID().toString();
    var dbResource = createPrivateDbResources(assignedUser, workspaceId, 1).get(0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures
            .makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId, assignedUser, PrivateResourceState.ACTIVE)
            .build();

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var resolution =
        new DatabaseResolution(dbResource.getName(), Optional.of(dbResource), Optional.empty());
    var result = step.validateDatabaseAccess(resolution);
    assertThat(result, equalTo(resolution));
  }

  @Test
  void testValidateDatabaseAccessPrivateButDifferentUser() {
    var workspaceId = UUID.randomUUID();
    var assignedUser = UUID.randomUUID().toString();
    var differentAssignedUser = UUID.randomUUID().toString();
    var dbResource = createPrivateDbResources(assignedUser, workspaceId, 1).get(0);

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures
            .makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId, differentAssignedUser, PrivateResourceState.ACTIVE)
            .build();

    var step =
        new CreateNamespaceRoleStep(
            workspaceId, mockAzureDatabaseUtilsRunner, resource, mockResourceDao);

    var resolution =
        new DatabaseResolution(dbResource.getName(), Optional.of(dbResource), Optional.empty());
    var result = step.validateDatabaseAccess(resolution);
    assertThat(result.errorMessage().isPresent(), equalTo(true));
  }

  @NotNull
  private List<ControlledAzureDatabaseResource> createSharedDbResources(
      String owner, UUID workspaceId, boolean allowAllWorkspaceUsers, int count) {
    return Stream.generate(
            () -> {
              var dbCreationParameters =
                  ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(owner, false)
                      .allowAccessForAllWorkspaceUsers(allowAllWorkspaceUsers);

              return ControlledAzureResourceFixtures
                  .makeSharedControlledAzureDatabaseResourceBuilder(
                      dbCreationParameters, workspaceId)
                  .build();
            })
        .limit(count)
        .toList();
  }

  @NotNull
  private List<ControlledAzureDatabaseResource> createPrivateDbResources(
      String assignedUser, UUID workspaceId, int count) {
    return Stream.generate(
            () -> {
              var dbCreationParameters =
                  ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, false);

              return ControlledAzureResourceFixtures
                  .makePrivateControlledAzureDatabaseResourceBuilder(
                      dbCreationParameters, workspaceId, assignedUser)
                  .build();
            })
        .limit(count)
        .toList();
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
