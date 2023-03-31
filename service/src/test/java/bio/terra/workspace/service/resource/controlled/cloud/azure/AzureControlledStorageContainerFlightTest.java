package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.TEST_AZURE_STORAGE_ACCOUNT_NAME;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.LandingZoneTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
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
  @Autowired private AzureStorageAccessService azureStorageAccessService;

  private Workspace sharedWorkspace;
  private UUID workspaceUuid;

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

  @Test
  public void createAndDeleteAzureStorageResource() throws InterruptedException {
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
                        TEST_AZURE_STORAGE_ACCOUNT_NAME,
                        containerName));
    assertEquals(404, exception.getResponse().getStatusCode());
  }
}
