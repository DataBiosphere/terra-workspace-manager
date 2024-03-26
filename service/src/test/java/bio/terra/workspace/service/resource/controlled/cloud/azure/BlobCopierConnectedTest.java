package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.TEST_AZURE_STORAGE_ACCOUNT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnectedPlus")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlobCopierConnectedTest extends BaseAzureConnectedTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(1);
  @Autowired private AzureStorageAccessService azureStorageAccessService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private CrlService crlService;
  @Autowired private AzureConfiguration azureConfig;
  @Autowired private AzureCloudContextService azureCloudContextService;

  private ControlledAzureStorageContainerResource sourceContainer;
  private ControlledAzureStorageContainerResource destContainer;
  private StorageAccount storageAcct;
  private AuthenticatedUserRequest userRequest;
  private UUID workspaceId;

  @BeforeEach
  void setup() throws InterruptedException {
    Workspace workspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
    workspaceId = workspace.getWorkspaceId();

    var azureCloudContext = azureCloudContextService.getAzureCloudContext(workspaceId).get();
    var storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);
    storageAcct =
        storageManager
            .storageAccounts()
            .getByResourceGroup(
                azureCloudContext.getAzureResourceGroupId(), TEST_AZURE_STORAGE_ACCOUNT_NAME);

    userRequest = userAccessUtils.defaultUserAuthRequest();

    var sourceScName = generateAzureResourceName("sc");
    sourceContainer =
        new ControlledAzureStorageContainerResource.Builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .build())
            .storageContainerName(sourceScName)
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
            .build();
    createResource(workspaceId, userRequest, destContainer);
  }

  @AfterEach
  void teardown() {
    workspaceService.deleteWorkspace(workspaceService.getWorkspace(workspaceId), userRequest);
  }

  @ParameterizedTest
  @MethodSource("getPrefixesToCopyAllFiles")
  void copyAllBlobs(@Nullable List<String> prefixesToCopy, String[] blobNames) {
    assertCopyBlobs(prefixesToCopy, blobNames, blobNames);
  }

  private static Stream<Arguments> getPrefixesToCopyAllFiles() {
    return Stream.of(
        Arguments.of(null, generateFilenames(6)),
        Arguments.of(List.of(), generateFilenames(3)),
        Arguments.of(List.of(""), generateFilenames(2)),
        Arguments.of(List.of("0/", "1/", "2/", "3/", "4/"), generateFilenames(5)));
  }

  @ParameterizedTest
  @MethodSource("getPrefixesToCopySomeFiles")
  void copyBlobsWithPrefix(List<String> prefixesToCopy, String[] allNames, String[] copiedNames) {
    assertCopyBlobs(prefixesToCopy, allNames, copiedNames);
  }

  private static Stream<Arguments> getPrefixesToCopySomeFiles() {
    var sixItems = generateFilenames(6);
    var folderWithThreeItems =
        new String[] {"folder/item1.txt", "folder/item2.txt", "folder/item3.txt"};
    return Stream.of(
        Arguments.of(
            List.of(" ", "/", "it-blob", "/it-blob"), generateFilenames(1), new String[] {}),
        Arguments.of(List.of("2", "5"), sixItems, new String[] {sixItems[2], sixItems[5]}),
        Arguments.of(List.of("folder/"), folderWithThreeItems, folderWithThreeItems),
        Arguments.of(
            List.of("folder/item2", "folder/item1.txt"),
            folderWithThreeItems,
            new String[] {folderWithThreeItems[0], folderWithThreeItems[1]}));
  }

  private void assertCopyBlobs(
      List<String> prefixesToCopy, String[] allNames, String[] copiedNames) {
    // upload blob to source container
    var sourceContainerClient =
        azureStorageAccessService.buildBlobContainerClient(sourceContainer, storageAcct);

    BlobCopier bc = new BlobCopier(azureStorageAccessService, userRequest);

    // do the copy
    uploadTestData(sourceContainerClient, List.of(allNames));
    var result =
        bc.copyBlobs(
            new StorageData(
                storageAcct.name(), storageAcct.endPoints().primary().blob(), sourceContainer),
            new StorageData(
                storageAcct.name(), storageAcct.endPoints().primary().blob(), destContainer),
            prefixesToCopy);

    assertFalse(result.anyFailures());
    var destClient = azureStorageAccessService.buildBlobContainerClient(destContainer, storageAcct);
    var copiedBlobs =
        destClient.listBlobs().stream()
            .filter(blobItem -> blobItem.getProperties().getContentLength() > 0)
            .map(BlobItem::getName)
            .collect(Collectors.toList());
    assertThat(copiedBlobs, everyItem(in(copiedNames)));
    assertEquals(copiedBlobs.size(), copiedNames.length);
  }

  private static String generateAzureResourceName(String tag) {
    String id = UUID.randomUUID().toString().substring(0, 6);
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

  private static String[] generateFilenames(int numberOfFiles) {
    return IntStream.range(0, numberOfFiles)
        .mapToObj(idx -> idx + "/it-blob-" + UUID.randomUUID())
        .collect(Collectors.toList())
        .toArray(new String[0]);
  }

  private void uploadTestData(BlobContainerClient containerClient, List<String> filenames) {
    for (String filename : filenames) {
      var binaryData = BinaryData.fromString("test data" + filename);
      containerClient.getBlobClient(filename).upload(binaryData);
    }
  }
}
