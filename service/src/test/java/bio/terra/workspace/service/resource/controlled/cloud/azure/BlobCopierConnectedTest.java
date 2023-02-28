package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import io.vavr.collection.Stream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnectedPlus")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlobCopierConnectedTest extends BaseAzureConnectedTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(1);
  @Autowired private AzureStorageAccessService azureStorageAccessService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;

  private ControlledAzureStorageContainerResource sourceContainer;
  private ControlledAzureStorageContainerResource destContainer;
  private ControlledAzureStorageResource storageAcct;
  private AuthenticatedUserRequest userRequest;
  private UUID workspaceId;

  @BeforeAll
  void setup() throws InterruptedException {
    userRequest = userAccessUtils.defaultUserAuthRequest();
    Workspace workspace = createWorkspaceWithCloudContext(workspaceService, userRequest);
    workspaceId = workspace.getWorkspaceId();

    var storageAccountId = UUID.randomUUID();
    var saName = generateAzureResourceName("sa");
    storageAcct =
        ControlledAzureStorageResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(storageAccountId)
                    .region("eastus")
                    .build())
            .storageAccountName(saName)
            .build();
    createResource(workspaceId, userRequest, storageAcct);

    var sourceScName = generateAzureResourceName("sc");
    sourceContainer =
        new ControlledAzureStorageContainerResource.Builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .build())
            .storageContainerName(sourceScName)
            .storageAccountId(storageAcct.getResourceId())
            .build();
    createResource(workspaceId, userRequest, sourceContainer);

    var destScName = generateAzureResourceName("sc");
    destContainer =
        new ControlledAzureStorageContainerResource.Builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .build())
            .storageContainerName(destScName)
            .storageAccountId(storageAcct.getResourceId())
            .build();
    createResource(workspaceId, userRequest, destContainer);
  }

  @AfterAll
  void teardown() {
    workspaceService.deleteWorkspace(workspaceService.getWorkspace(workspaceId), userRequest);
  }

  @Test
  void copyBlobs() {
    // upload blob to source container
    var sourceContainerClient =
        azureStorageAccessService.buildBlobContainerClient(sourceContainer, storageAcct);

    BlobCopier bc = new BlobCopier(azureStorageAccessService, userRequest);

    // do the copy
    var sourceBlobs = uploadTestData(sourceContainerClient, 10);
    var result =
        bc.copyBlobs(
            new StorageData(
                storageAcct.getStorageAccountName(),
                storageAcct.getStorageAccountEndpoint(),
                sourceContainer),
            new StorageData(
                storageAcct.getStorageAccountName(),
                storageAcct.getStorageAccountEndpoint(),
                destContainer));

    assertFalse(result.anyFailures());
    var destClient = azureStorageAccessService.buildBlobContainerClient(destContainer, storageAcct);
    var copiedBlobs =
        destClient.listBlobs().stream().map(BlobItem::getName).collect(Collectors.toList());
    assertThat(copiedBlobs, everyItem(in(sourceBlobs)));
  }

  private static String generateAzureResourceName(String tag) {
    final String id = UUID.randomUUID().toString().substring(0, 6);
    return String.format("it%s%s", tag, id);
  }

  private void createResource(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest, ControlledResource resource)
      throws InterruptedException {
    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, resource, null),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());
  }

  private List<String> uploadTestData(BlobContainerClient containerClient, int numberOfFiles) {
    return Stream.range(0, numberOfFiles)
        .map(
            idx -> {
              var fileId = UUID.randomUUID();
              var binaryData = BinaryData.fromString("test data" + fileId);
              var blobName = "it-blob-" + fileId;
              containerClient.getBlobClient(blobName).upload(binaryData);
              return blobName;
            })
        .collect(Collectors.toList());
  }
}
