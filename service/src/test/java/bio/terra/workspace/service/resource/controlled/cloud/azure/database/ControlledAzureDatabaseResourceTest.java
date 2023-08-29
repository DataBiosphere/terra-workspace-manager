package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.generated.model.ApiAzureDatabaseAttributes;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.CreateFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetPetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetWorkspaceManagedIdentityStep;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@Tag("azure-unit")
public class ControlledAzureDatabaseResourceTest extends BaseMockitoStrictStubbingTest {
  private final UUID workspaceId = UUID.randomUUID();
  @Mock private FlightBeanBag mockFlightBeanBag;

  @Test
  void testCorrectPrivateDatabaseSteps() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, "default");

    var databaseResource =
        ControlledAzureResourceFixtures.makePrivateControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId, UUID.randomUUID().toString())
            .build();

    var steps = databaseResource.getAddSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        contains(
            ValidateDatabaseOwnerStep.class,
            AzureDatabaseGuardStep.class,
            GetPetManagedIdentityStep.class,
            GetFederatedIdentityStep.class,
            CreateFederatedIdentityStep.class,
            CreateAzureDatabaseStep.class));
  }

  @Test
  void testCorrectSharedDatabaseSteps() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID(), "default");

    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var steps = databaseResource.getAddSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        contains(
            ValidateDatabaseOwnerStep.class,
            AzureDatabaseGuardStep.class,
            GetWorkspaceManagedIdentityStep.class,
            GetFederatedIdentityStep.class,
            CreateFederatedIdentityStep.class,
            CreateAzureDatabaseStep.class));
  }

  @Test
  void testToApiResource() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID(), "default");

    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var apiResource = databaseResource.toApiResource();
    assertThat(
        apiResource.getAttributes(),
        equalTo(
            new ApiAzureDatabaseAttributes()
                .databaseName(creationParameters.getName())
                .databaseOwner(creationParameters.getOwner())
                .allowAccessForAllWorkspaceUsers(
                    creationParameters.isAllowAccessForAllWorkspaceUsers())));
  }

  @Test
  void testAttributesToJson() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID(), "default");

    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var json = databaseResource.attributesToJson();
    assertThat(
        json,
        equalTo(
            """
            {"databaseName":"%s","databaseOwner":"%s","k8sNamespace":"%s","allowAccessForAllWorkspaceUsers":%s}"""
                .formatted(
                    creationParameters.getName(),
                    creationParameters.getOwner(),
                    creationParameters.getK8sNamespace(),
                    creationParameters.isAllowAccessForAllWorkspaceUsers())));
  }
}
