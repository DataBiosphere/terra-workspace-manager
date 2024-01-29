package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.generated.model.ApiAzureDatabaseAttributes;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class ControlledAzureDatabaseResourceTest extends BaseMockitoStrictStubbingTest {
  private final UUID workspaceId = UUID.randomUUID();
  @Mock private FlightBeanBag mockFlightBeanBag;

  @Test
  void testCorrectCreateDatabaseSteps() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, false);

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
            CreateAzureDatabaseStep.class));
  }

  @Test
  void testToApiResource() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(
            UUID.randomUUID().toString(), false);

    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId, CloningInstructions.COPY_NOTHING)
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
            UUID.randomUUID().toString(), false);

    var databaseResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                creationParameters, workspaceId, CloningInstructions.COPY_NOTHING)
            .build();

    var json = databaseResource.attributesToJson();
    assertThat(
        json,
        equalTo(
            """
            {"databaseName":"%s","databaseOwner":"%s","allowAccessForAllWorkspaceUsers":%s}"""
                .formatted(
                    creationParameters.getName(),
                    creationParameters.getOwner(),
                    creationParameters.isAllowAccessForAllWorkspaceUsers())));
  }
}
