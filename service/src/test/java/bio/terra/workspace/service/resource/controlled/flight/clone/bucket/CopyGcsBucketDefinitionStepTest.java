package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_WORKSPACE_ID;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_RESOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class CopyGcsBucketDefinitionStepTest extends BaseUnitTest {

  @Mock private FlightContext mockFlightContext;
  @Mock private AuthenticatedUserRequest mockUserRequest;
  @Mock private ControlledResourceService mockControlledResourceService;
  private CopyGcsBucketDefinitionStep copyGcsBucketDefinitionStep;

  @BeforeEach
  public void setup() {
    copyGcsBucketDefinitionStep = new CopyGcsBucketDefinitionStep(
        mockUserRequest,
        SOURCE_BUCKET_RESOURCE,
        mockControlledResourceService,
        SOURCE_BUCKET_RESOURCE.getCloningInstructions());
  }

  @Test
  public void testDoStep_copyNothing_inputParameter() throws InterruptedException {
    final var inputParameters = new FlightMap();
    // override the bucket's instructions via input parameter
    inputParameters.put(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.COPY_NOTHING);
    final var workingMap = new FlightMap();
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();
    final StepResult stepResult = copyGcsBucketDefinitionStep.doStep(mockFlightContext);
    verifyNoInteractions(mockControlledResourceService);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);
  }

  @Test
  public void testDoStep_copyDefinition() throws InterruptedException {
    final var inputParameters = new FlightMap();
    inputParameters.put(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.COPY_DEFINITION);
    inputParameters.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, DESTINATION_WORKSPACE_ID);
    inputParameters.put(ResourceKeys.RESOURCE_NAME, "source-resource");
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    final var workingMap = new FlightMap();
    workingMap.put(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, "A bucket with a hole in it.");
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();

    final StepResult stepResult = copyGcsBucketDefinitionStep.doStep(mockFlightContext);
    final ArgumentCaptor<ControlledGcsBucketResource> destinationBucketCaptor =
        ArgumentCaptor.forClass(ControlledGcsBucketResource.class);
    final ArgumentCaptor<ControlledResourceIamRole> iamRoleCaptor =
        ArgumentCaptor.forClass(ControlledResourceIamRole.class);
    final ArgumentCaptor<ApiGcpGcsBucketCreationParameters> creationParametersCaptor =
        ArgumentCaptor.forClass(ApiGcpGcsBucketCreationParameters.class);
    verify(mockControlledResourceService).createControlledResourceSync(
        destinationBucketCaptor.capture(),
        iamRoleCaptor.capture(),
        any(AuthenticatedUserRequest.class),
        creationParametersCaptor.capture());
    assertEquals(StepResult.getStepResultSuccess(), stepResult);
  }
}
