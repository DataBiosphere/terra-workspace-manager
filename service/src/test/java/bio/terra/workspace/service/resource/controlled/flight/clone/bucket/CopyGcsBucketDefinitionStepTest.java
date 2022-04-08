package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.CREATED_BUCKET_RESOURCE;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_WORKSPACE_ID;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_RESOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
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
        CloningInstructions.COPY_DEFINITION);
  }

  @Test
  public void testDoStep() throws InterruptedException {
    final var inputParameters = new FlightMap();
    inputParameters.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, DESTINATION_WORKSPACE_ID);
    inputParameters.put(ResourceKeys.RESOURCE_NAME, "source-resource");
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    final var workingMap = new FlightMap();
    workingMap.put(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, "A bucket with a hole in it.");
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();
    doReturn(CREATED_BUCKET_RESOURCE).when(mockControlledResourceService)
        .createControlledResourceSync(
            any(ControlledResource.class),
            any(ControlledResourceIamRole.class),
            any(AuthenticatedUserRequest.class),
            any());

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
    // todo: assert all the captured values
    assertEquals(StepResult.getStepResultSuccess(), stepResult);
  }
}
