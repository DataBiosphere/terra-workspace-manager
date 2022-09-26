package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.CREATED_BUCKET_RESOURCE;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_WORKSPACE_ID;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_CREATION_PARAMETERS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_RESOURCE;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_RESOURCE_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_WORKSPACE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;

public class CopyGcsBucketDefinitionStepTest extends BaseUnitTest {

  @MockBean private FlightContext mockFlightContext;
  @MockBean private AuthenticatedUserRequest mockUserRequest;
  @MockBean private ControlledResourceService mockControlledResourceService;
  @MockBean private GcpCloudContextService gcpCloudContextService;
  private CopyGcsBucketDefinitionStep copyGcsBucketDefinitionStep;

  @BeforeEach
  public void setup() throws InterruptedException {
    copyGcsBucketDefinitionStep =
        new CopyGcsBucketDefinitionStep(
            mockUserRequest,
            SOURCE_BUCKET_RESOURCE,
            mockControlledResourceService,
            CloningInstructions.COPY_DEFINITION);
    when(gcpCloudContextService.getRequiredGcpProject(any(UUID.class)))
        .thenReturn("my-fake-project");
  }

  @Test
  public void testDoStep() throws InterruptedException {
    final var inputParameters = new FlightMap();
    inputParameters.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, DESTINATION_WORKSPACE_ID);
    inputParameters.put(
        ResourceKeys.RESOURCE_NAME, GcsBucketCloneTestFixtures.SOURCE_RESOURCE_NAME);
    inputParameters.put(ControlledResourceKeys.DESTINATION_BUCKET_NAME, DESTINATION_BUCKET_NAME);
    inputParameters.put(
        ControlledResourceKeys.CREATION_PARAMETERS, SOURCE_BUCKET_CREATION_PARAMETERS);
    inputParameters.put(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.randomUUID());
    doReturn(inputParameters).when(mockFlightContext).getInputParameters();

    final var workingMap = new FlightMap();
    workingMap.put(
        ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
        GcsBucketCloneTestFixtures.SOURCE_BUCKET_DESCRIPTION);
    workingMap.put(ControlledResourceKeys.CREATION_PARAMETERS, SOURCE_BUCKET_CREATION_PARAMETERS);
    doReturn(workingMap).when(mockFlightContext).getWorkingMap();

    doReturn(CREATED_BUCKET_RESOURCE)
        .when(mockControlledResourceService)
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
    verify(mockControlledResourceService)
        .createControlledResourceSync(
            destinationBucketCaptor.capture(),
            iamRoleCaptor.capture(),
            any(AuthenticatedUserRequest.class),
            creationParametersCaptor.capture());

    final ControlledGcsBucketResource destinationBucketResource =
        destinationBucketCaptor.getValue();
    assertEquals(DESTINATION_BUCKET_NAME, destinationBucketResource.getBucketName());
    assertEquals(DESTINATION_WORKSPACE_ID, destinationBucketResource.getWorkspaceId());
    assertNotNull(destinationBucketResource.getResourceId());
    assertEquals(SOURCE_RESOURCE_NAME, destinationBucketResource.getName());
    assertEquals(
        GcsBucketCloneTestFixtures.SOURCE_BUCKET_DESCRIPTION,
        destinationBucketResource.getDescription());
    assertEquals(
        SOURCE_BUCKET_RESOURCE.getCloningInstructions(),
        destinationBucketResource.getCloningInstructions());
    assertEquals(
        SOURCE_BUCKET_RESOURCE.getAssignedUser(), destinationBucketResource.getAssignedUser());
    assertEquals(AccessScopeType.ACCESS_SCOPE_PRIVATE, destinationBucketResource.getAccessScope());
    assertEquals(SOURCE_BUCKET_RESOURCE.getManagedBy(), destinationBucketResource.getManagedBy());
    assertEquals(
        SOURCE_BUCKET_RESOURCE.getApplicationId(), destinationBucketResource.getApplicationId());
    assertEquals(
        SOURCE_BUCKET_RESOURCE.getPrivateResourceState(),
        destinationBucketResource.getPrivateResourceState());
    var lineage = destinationBucketResource.getResourceLineage();
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    expectedLineage.add(
        new ResourceLineageEntry(SOURCE_WORKSPACE_ID, SOURCE_BUCKET_RESOURCE.getResourceId()));
    assertEquals(expectedLineage, lineage);

    final ControlledResourceIamRole controlledResourceIamRole = iamRoleCaptor.getValue();
    assertEquals(ControlledResourceIamRole.EDITOR, controlledResourceIamRole);

    final ApiGcpGcsBucketCreationParameters bucketCreationParameters =
        creationParametersCaptor.getValue();
    assertEquals(SOURCE_BUCKET_CREATION_PARAMETERS, bucketCreationParameters);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);
  }
}
