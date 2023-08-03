package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
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
public class ValidateDatabaseOwnerStepTest {
  private MockitoSession mockito;
  @Mock private FlightContext mockFlightContext;
  @Mock private ResourceDao mockResourceDao;
  @Mock private WsmResource mockWsmResource;

  private final UUID owner = UUID.randomUUID();
  private final ApiAzureDatabaseCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(owner, "default");
  private final ControlledAzureDatabaseResource databaseResource =
      ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
              creationParameters, UUID.randomUUID())
          .build();

  @BeforeEach
  public void setup() {
    // initialize session to start mocking
    mockito =
        Mockito.mockitoSession().initMocks(this).strictness(Strictness.STRICT_STUBS).startMocking();
  }

  @AfterEach
  public void tearDown() {
    mockito.finishMocking();
  }

  @Test
  void testExists() throws InterruptedException {
    when(mockResourceDao.getResource(databaseResource.getWorkspaceId(), owner))
        .thenReturn(mockWsmResource);
    when(mockWsmResource.getResourceType())
        .thenReturn(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);

    var result =
        new ValidateDatabaseOwnerStep(databaseResource, mockResourceDao).doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testNoOwner() throws InterruptedException {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(null, "default");
    var databaseResource =
        ControlledAzureResourceFixtures.makePrivateControlledAzureDatabaseResourceBuilder(
                creationParameters, UUID.randomUUID(), null)
            .build();

    var result =
        new ValidateDatabaseOwnerStep(databaseResource, mockResourceDao).doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testDoesNotExists() throws InterruptedException {
    when(mockResourceDao.getResource(databaseResource.getWorkspaceId(), owner))
        .thenThrow(new ResourceNotFoundException("not found"));

    var result =
        new ValidateDatabaseOwnerStep(databaseResource, mockResourceDao).doStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testWrongType() throws InterruptedException {
    when(mockResourceDao.getResource(databaseResource.getWorkspaceId(), owner))
        .thenReturn(mockWsmResource);
    when(mockWsmResource.getResourceType()).thenReturn(WsmResourceType.CONTROLLED_AZURE_DATABASE);

    var result =
        new ValidateDatabaseOwnerStep(databaseResource, mockResourceDao).doStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }
}
