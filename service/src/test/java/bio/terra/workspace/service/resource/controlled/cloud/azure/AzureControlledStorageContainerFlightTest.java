package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
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
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.LandingZoneTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class AzureControlledStorageContainerFlightTest extends BaseAzureConnectedTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(15);

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private LandingZoneTestUtils landingZoneTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private LandingZoneDao landingZoneDao;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private CrlService crlService;
  @Autowired private AzureConfiguration azureConfig;
  @Autowired private AzureStorageAccessService azureStorageAccessService;

  private Workspace sharedWorkspace;
  private TestLandingZoneManager testLandingZoneManager;
  private UUID workspaceUuid;
  private String storageAccountName;

  @BeforeAll
  public void setup() throws InterruptedException {
    sharedWorkspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
    workspaceUuid = sharedWorkspace.getWorkspaceId();
  }

  @AfterAll
  public void cleanup() {
    workspaceService.deleteWorkspace(sharedWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  private void setupLandingZone() {
    // create quasi landing zone with a single resource - shared storage account
    storageAccountName = String.format("lzsharedstacc%s", TestUtils.getRandomString(6));
    UUID landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());
    testLandingZoneManager =
        new TestLandingZoneManager(
            azureCloudContextService,
            landingZoneDao,
            workspaceDao,
            crlService,
            azureConfig,
            workspaceUuid);

    testLandingZoneManager.createLandingZoneWithSharedStorageAccount(
        landingZoneId, workspaceUuid, storageAccountName, DEFAULT_AZURE_RESOURCE_REGION);
  }

  private void cleanupLandingZone() {
    testLandingZoneManager.deleteLandingZoneWithSharedStorageAccount(
        UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId()),
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        storageAccountName);
  }

  @Test
  public void createAndDeleteAzureStorageResource() throws InterruptedException {
    setupLandingZone();
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Submit a storage container creation flight and then verify the resource exists in the
    // workspace.
    final UUID sharedContainerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource sharedContainerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
            sharedContainerResourceId,
            storageContainerName,
            getAzureName("rc"),
            getAzureName("rc-desc"));

    azureUtils.createResource(
        workspaceUuid,
        userRequest,
        sharedContainerResource,
        WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    TimeUnit.MINUTES.sleep(1);

    // create SAS token for the storage container above validate
    OffsetDateTime sharedStartTime = OffsetDateTime.now();
    OffsetDateTime sharedExpiryTime = sharedStartTime.plusMinutes(15L);
    var sharedAzureSasBundle =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            workspaceUuid,
            sharedContainerResourceId,
            userRequest,
            new SasTokenOptions(null, sharedStartTime, sharedExpiryTime, null, null));
    assertNotNull(sharedAzureSasBundle);
    assertNotNull(sharedAzureSasBundle.sasToken());
    assertNotNull(sharedAzureSasBundle.sasUrl());

    // clean up resources - delete storage container resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        sharedContainerResource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        sharedContainerResource.getStorageContainerName(),
        null); // Don't sleep/verify deletion yet.

    // Verify containers have been deleted (Can't do this in submitControlledResourceDeletionFlight
    // because the get function takes a different number of arguments. Also no need to sleep another
    // 5 seconds.)
    verifyStorageAccountContainerIsDeleted(storageContainerName);
    cleanupLandingZone();
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

  @Disabled("TODO(TOAZ-286): Re-enable this test when the ticket is fixed")
  @Test
  public void
      createAzureStorageContainerFlightFailedBecauseLandingZoneDoesntHaveSharedStorageAccount()
          throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // create quasi landing zone without resources
    UUID alternateLandingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());

    testLandingZoneManager.createLandingZoneWithoutResources(alternateLandingZoneId, workspaceUuid);

    // Submit a storage container creation flight which should error out
    final UUID containerResourceId = UUID.randomUUID();
    final String storageContainerName = ControlledResourceFixtures.uniqueBucketName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            workspaceUuid,
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
    assertEquals(ResourceNotFoundException.class, flightState.getException().get().getClass());

    // clean up resources - delete alternate lz database record only
    testLandingZoneManager.deleteLandingZoneWithoutResources(alternateLandingZoneId);
  }

  private void verifyStorageAccountContainerIsDeleted(String containerName) {
    com.azure.core.exception.HttpResponseException exception =
        assertThrows(
            com.azure.core.exception.HttpResponseException.class,
            () ->
                azureTestUtils
                    .getStorageManager()
                    .blobContainers()
                    .get(
                        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
                        storageAccountName,
                        containerName));
    assertEquals(404, exception.getResponse().getStatusCode());
  }
}
