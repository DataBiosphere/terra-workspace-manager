package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_WORKSPACE_ID;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_CREATION_PARAMETERS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_RESOURCE;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_RESOURCE_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_WORKSPACE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTestMockGcpCloudContextService;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.storage.Storage;
import com.google.cloud.Policy;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

// TODO: PF-2090 - Spring does not seem to notice that it needs to build a different
//  application context for this test class, even though it inherits a different set
//  of mocks. Doing @DirtiesContext forces a new application context and it is built
//  properly. See the ticket for more details.
@DirtiesContext(classMode = BEFORE_CLASS)
public class CopyGcsBucketDefinitionStepTest extends BaseUnitTestMockGcpCloudContextService {
  private CopyGcsBucketDefinitionStep copyGcsBucketDefinitionStep;

  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ControlledResourceService controlledResourceService;

  @Mock FlightContext mockFlightContext;
  @Mock private StorageCow mockStorageCow;
  @Mock private Storage mockStorageClient;
  @Mock private Storage.Buckets mockBuckets;
  @Mock private Storage.Buckets.Get mockStorageBucketsGet;
  @Mock private GcpCloudContext mockGcpCloudContext;
  @Mock private Policy mockPolicy;

  private static final String PROJECT_ID = "my-project-id";
  private static final String POLICY_GROUP = "fake-policy-group";

  @BeforeEach
  public void setup() throws InterruptedException, IOException {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(DESTINATION_WORKSPACE_ID).build();
    workspaceDao.createWorkspace(workspace, /*applicationIds=*/ null);
    WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(
        workspaceDao, DESTINATION_WORKSPACE_ID, PROJECT_ID);

    when(mockCrlService().createStorageCow(any(String.class))).thenReturn(mockStorageCow);
    when(mockCrlService().createWsmSaNakedStorageClient()).thenReturn(mockStorageClient);
    when(mockStorageCow.getIamPolicy(any(String.class))).thenReturn(mockPolicy);
    when(mockPolicy.getBindingsList()).thenReturn(ImmutableList.copyOf(new ArrayList<>()));
    when(mockSamService()
            .syncResourcePolicy(
                any(ControlledResource.class),
                any(ControlledResourceIamRole.class),
                any(AuthenticatedUserRequest.class)))
        .thenReturn(POLICY_GROUP);
    when(mockSamService().getUserEmailFromSam(any())).thenReturn(USER_REQUEST.getEmail());
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userSubjectId(USER_REQUEST.getSubjectId())
                .userEmail(USER_REQUEST.getEmail()));
    when(mockStorageClient.buckets()).thenReturn(mockBuckets);
    when(mockBuckets.get(any(String.class))).thenReturn(mockStorageBucketsGet);
    GoogleJsonResponseException fakeNotFoundError =
        new GoogleJsonResponseException(
            new HttpResponseException.Builder(403, "fake not found error", new HttpHeaders()),
            /*details=*/ null);
    when(mockStorageBucketsGet.execute()).thenThrow(fakeNotFoundError);

    copyGcsBucketDefinitionStep =
        new CopyGcsBucketDefinitionStep(
            mockSamService(),
            USER_REQUEST,
            SOURCE_BUCKET_RESOURCE,
            controlledResourceService,
            CloningInstructions.COPY_DEFINITION);

    when(mockGcpCloudContextService().getRequiredGcpProject(any(UUID.class)))
        .thenReturn(PROJECT_ID);
    when(mockGcpCloudContextService()
            .getRequiredGcpCloudContext(any(UUID.class), any(AuthenticatedUserRequest.class)))
        .thenReturn(mockGcpCloudContext);
    when(mockGcpCloudContext.getGcpProjectId()).thenReturn(PROJECT_ID);
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
    when(mockFlightContext.getInputParameters()).thenReturn(inputParameters);

    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));

    final var workingMap = new FlightMap();
    workingMap.put(
        ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
        GcsBucketCloneTestFixtures.SOURCE_BUCKET_DESCRIPTION);
    workingMap.put(ControlledResourceKeys.CREATION_PARAMETERS, SOURCE_BUCKET_CREATION_PARAMETERS);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);

    final StepResult stepResult = copyGcsBucketDefinitionStep.doStep(mockFlightContext);

    ControlledGcsBucketResource destinationBucketResource =
        workingMap.get(
            ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, ControlledGcsBucketResource.class);
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
        "Optional[ACTIVE]", destinationBucketResource.getPrivateResourceState().toString());
    var lineage = destinationBucketResource.getResourceLineage();
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    expectedLineage.add(
        new ResourceLineageEntry(SOURCE_WORKSPACE_ID, SOURCE_BUCKET_RESOURCE.getResourceId()));
    assertEquals(expectedLineage, lineage);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);
  }
}
