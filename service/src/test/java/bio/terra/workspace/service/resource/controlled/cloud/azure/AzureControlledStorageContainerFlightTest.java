package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.AzureConnectedTestUtils;
import bio.terra.workspace.connected.LandingZoneTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(Lifecycle.PER_CLASS)
public class AzureControlledStorageContainerFlightTest extends BaseAzureConnectedTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(15);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private LandingZoneTestUtils landingZoneTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private LandingZoneDao landingZoneDao;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private CrlService crlService;
  @Autowired private AzureConfiguration azureConfig;
  @Autowired private AzureConnectedTestUtils azureUtils;
  @Autowired private AzureStorageAccessService azureStorageAccessService;

  private Workspace sharedWorkspace;
  private UUID workspaceUuid;

  @BeforeAll
  public void setup() throws InterruptedException {
    sharedWorkspace = azureTestUtils.createWorkspace(workspaceService);
    workspaceUuid = sharedWorkspace.getWorkspaceId();
    azureUtils.createCloudContext(workspaceUuid, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterAll
  public void cleanup() {
    workspaceService.deleteWorkspace(sharedWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  public void createAndDeleteAzureStorageResource() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    final ApiAzureStorageCreationParameters accountCreationParameters =
        ControlledResourceFixtures.getAzureStorageCreationParameters();

    final UUID accountResourceId = UUID.randomUUID();
    ControlledAzureStorageResource accountResource =
        ControlledResourceFixtures.getAzureStorage(
            workspaceUuid,
            accountResourceId,
            accountCreationParameters.getStorageAccountName(),
            accountCreationParameters.getRegion(),
            getAzureName("rs"),
            getAzureName("rs-desc"));

    // Submit a storage account creation flight and then verify the resource exists in the
    // workspace.
    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        accountResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID containerResourceId = UUID.randomUUID();
    final String containerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
            accountResourceId,
            containerResourceId,
            containerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));
    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        containerResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    // clean up resources - delete storage container resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        containerResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        containerResource.getStorageContainerName(),
        null); // Don't sleep/verify deletion yet.

    // clean up resources - delete storage account resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        accountResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        accountResource.getStorageAccountName(),
        azureTestUtils.getStorageManager().storageAccounts()::getByResourceGroup);

    // Verify containers have been deleted (Can't do this in submitControlledResourceDeletionFlight
    // because the get function takes a different number of arguments. Also no need to sleep another
    // 5 seconds.)
    verifyStorageAccountContainerIsDeleted(accountResource, containerName);
  }

  @Test
  public void createAndDeleteAzureStorageContainerBasedOnLandingZoneSharedStorageAccount()
      throws InterruptedException {
    String storageAccountName = String.format("lzsharedstacc%s", TestUtils.getRandomString(6));

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // create quasi landing zone with a single resource - shared storage account
    UUID landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());

    TestLandingZoneManager testLandingZoneManager =
        new TestLandingZoneManager(
            azureCloudContextService,
            landingZoneDao,
            workspaceDao,
            crlService,
            azureConfig,
            workspaceUuid);

    testLandingZoneManager.createLandingZoneWithSharedStorageAccount(
        landingZoneId, workspaceUuid, storageAccountName, "eastus");

    TimeUnit.MINUTES.sleep(1);

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID containerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
            null,
            containerResourceId,
            storageContainerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        containerResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    TimeUnit.MINUTES.sleep(1);

    // clean up resources - delete storage container resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        containerResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        containerResource.getStorageContainerName(),
        null); // Don't sleep/verify deletion yet.

    // clean up resources - delete lz database record and storage account
    testLandingZoneManager.deleteLandingZoneWithSharedStorageAccount(
        landingZoneId,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        storageAccountName);
  }

  @Test
  public void createAzureStorageContainerFlightFailedBecauseLandingZoneDoesntExist()
      throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // landing zone doesn't exist

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID containerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
            null,
            containerResourceId,
            storageContainerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, containerResource, null),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(flightState.getException().isPresent());
    assertEquals(flightState.getException().get().getClass(), LandingZoneNotFoundException.class);

    // no need to clean up resources
  }

  @Test
  public void
      createAzureStorageContainerFlightFailedBecauseLandingZoneDoesntHaveSharedStorageAccount()
          throws InterruptedException {
    // Unlike other tests, this one uses a different landing zone and so cannot share a cloud
    // context
    Workspace workspace = azureTestUtils.createWorkspace(workspaceService);
    UUID workspaceId = workspace.getWorkspaceId();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    azureUtils.createCloudContext(workspaceId, userRequest);

    // create quasi landing zone without resources
    UUID landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());

    TestLandingZoneManager testLandingZoneManager =
        new TestLandingZoneManager(
            azureCloudContextService,
            landingZoneDao,
            workspaceDao,
            crlService,
            azureConfig,
            workspaceId);

    testLandingZoneManager.createLandingZoneWithoutResources(landingZoneId, workspaceId);

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID containerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceId,
            null,
            containerResourceId,
            storageContainerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceId, userRequest, containerResource, null),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.ERROR, flightState.getFlightStatus());
    assertTrue(flightState.getException().isPresent());
    assertEquals(flightState.getException().get().getClass(), ResourceNotFoundException.class);

    // clean up resources - delete lz database record only
    testLandingZoneManager.deleteLandingZoneWithoutResources(landingZoneId);
  }

  @Test
  public void generateAzureStorageContainerSasToken() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    final ApiAzureStorageCreationParameters accountCreationParameters =
        ControlledResourceFixtures.getAzureStorageCreationParameters();

    final UUID accountResourceId = UUID.randomUUID();
    ControlledAzureStorageResource accountResource =
        ControlledResourceFixtures.getAzureStorage(
            workspaceUuid,
            accountResourceId,
            accountCreationParameters.getStorageAccountName(),
            accountCreationParameters.getRegion(),
            getAzureName("rs"),
            getAzureName("rs-desc"));

    // Submit a storage account creation flight and then verify the resource exists in the
    // workspace.
    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        accountResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID containerResourceId = UUID.randomUUID();
    final String containerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
            accountResourceId,
            containerResourceId,
            containerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        containerResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    TimeUnit.SECONDS.sleep(15);

    // create SAS token for the storage container above validate
    OffsetDateTime startTime = OffsetDateTime.now();
    OffsetDateTime expiryTime = startTime.plusMinutes(15L);
    var azureSasBundle =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            workspaceUuid,
            containerResourceId,
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, null, null));
    assertNotNull(azureSasBundle);
    assertNotNull(azureSasBundle.sasToken());
    assertNotNull(azureSasBundle.sasUrl());

    // clean up resources - delete storage container resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        containerResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        containerResource.getStorageContainerName(),
        null); // Don't sleep/verify deletion yet.

    // clean up resources - delete storage account resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        accountResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        accountResource.getStorageAccountName(),
        azureTestUtils.getStorageManager().storageAccounts()::getByResourceGroup);

    verifyStorageAccountContainerIsDeleted(accountResource, containerName);
  }

  @Test
  public void generateAzureStorageContainerSasTokenBasedOnLandingZoneSharedStorageAccount()
      throws InterruptedException {
    // name of the shared storage account in landing zone
    String storageAccountName = String.format("lzsharedsa%s", Instant.now().getEpochSecond());

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // create quasi landing zone with a single resource - shared storage account
    UUID landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());

    TestLandingZoneManager testLandingZoneManager =
        new TestLandingZoneManager(
            azureCloudContextService,
            landingZoneDao,
            workspaceDao,
            crlService,
            azureConfig,
            workspaceUuid);

    testLandingZoneManager.createLandingZoneWithSharedStorageAccount(
        landingZoneId, workspaceUuid, storageAccountName, "eastus");

    TimeUnit.MINUTES.sleep(1);

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID containerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
            null,
            containerResourceId,
            storageContainerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));

    // create azure storage container
    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        containerResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    TimeUnit.MINUTES.sleep(1);

    // create SAS token for the storage container above validate
    OffsetDateTime startTime = OffsetDateTime.now();
    OffsetDateTime expiryTime = startTime.plusMinutes(15L);
    var azureSasBundle =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            workspaceUuid,
            containerResourceId,
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, null, null));
    assertNotNull(azureSasBundle);
    assertNotNull(azureSasBundle.sasToken());
    assertNotNull(azureSasBundle.sasUrl());

    // clean up resources - delete lz database record and storage account
    testLandingZoneManager.deleteLandingZoneWithSharedStorageAccount(
        landingZoneId,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        storageAccountName);
  }

  private void verifyStorageAccountContainerIsDeleted(
      ControlledAzureStorageResource accountResource, String containerName) {
    com.azure.core.exception.HttpResponseException exception =
        assertThrows(
            com.azure.core.exception.HttpResponseException.class,
            () ->
                azureTestUtils
                    .getStorageManager()
                    .blobContainers()
                    .get(
                        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                        accountResource.getStorageAccountName(),
                        containerName));
    assertEquals(404, exception.getResponse().getStatusCode());
  }
}
