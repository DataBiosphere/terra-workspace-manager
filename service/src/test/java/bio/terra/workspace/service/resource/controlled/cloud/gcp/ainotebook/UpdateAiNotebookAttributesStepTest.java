package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.AI_NOTEBOOK_PREV_PARAMETERS;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.AI_NOTEBOOK_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_PARAMETERS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultAiNotebookInstance;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow.Instances;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks.Projects.Locations.Instances.UpdateMetadataItems;
import java.io.IOException;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class UpdateAiNotebookAttributesStepTest extends BaseUnitTest {

  private static final String PROJECT_ID = "my-project-id";

  private UpdateAiNotebookAttributesStep updateAiNotebookAttributesStep;
  private ControlledAiNotebookInstanceResource resource;

  @Mock private CrlService crlService;
  @Mock private FlightContext context;
  @Mock private GcpCloudContextService cloudContextService;
  @Mock private AIPlatformNotebooksCow notebooksCow;
  @Mock private Instances instances;

  @Captor private ArgumentCaptor<HashMap<String, String>> metadataArgCaptor;
  @BeforeEach
  public void setUp() throws IOException {
    final FlightMap inputParams = new FlightMap();
    inputParams.put(UPDATE_PARAMETERS, AI_NOTEBOOK_UPDATE_PARAMETERS);
    inputParams.put(PREVIOUS_UPDATE_PARAMETERS, AI_NOTEBOOK_PREV_PARAMETERS);
    when(context.getInputParameters()).thenReturn(inputParams);

    when(crlService.getAIPlatformNotebooksCow()).thenReturn(notebooksCow);


    resource = makeDefaultAiNotebookInstance().build();
    when(notebooksCow.instances()).thenReturn(instances);
    when(instances.updateMetadataItems(eq(resource.toInstanceName(PROJECT_ID)),
            metadataArgCaptor.capture())).thenReturn(null);
    when(cloudContextService.getRequiredGcpProject(eq(resource.getWorkspaceId()))).thenReturn(PROJECT_ID);
    updateAiNotebookAttributesStep = new UpdateAiNotebookAttributesStep(
        resource, crlService, cloudContextService);
  }

  @Test
  public void doStep() throws InterruptedException, IOException {
    var result = updateAiNotebookAttributesStep.doStep(context);
    assertEquals(StepResult.getStepResultSuccess(), result);

    verify(instances, times(1))
        .updateMetadataItems(eq(resource.toInstanceName(PROJECT_ID)), eq(AI_NOTEBOOK_UPDATE_PARAMETERS.getMetadata()));
    assertEquals(AI_NOTEBOOK_UPDATE_PARAMETERS.getMetadata(), metadataArgCaptor.getValue());
  }

  @Test
  public void undoStep() throws InterruptedException, IOException {
    var result = updateAiNotebookAttributesStep.undoStep(context);
    assertEquals(StepResult.getStepResultSuccess(), result);

    verify(instances, times(1))
        .updateMetadataItems(eq(resource.toInstanceName(PROJECT_ID)), eq(AI_NOTEBOOK_PREV_PARAMETERS.getMetadata()));
    assertEquals(AI_NOTEBOOK_PREV_PARAMETERS.getMetadata(), metadataArgCaptor.getValue());
  }
}
