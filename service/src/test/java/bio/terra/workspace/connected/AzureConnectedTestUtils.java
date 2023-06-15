package bio.terra.workspace.connected;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@Profile("azure-test")
@Component
public class AzureConnectedTestUtils {
  public static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(15);

  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private AzureCloudContextService azureCloudContextService;

  public void createCloudContext(UUID workspaceUuid, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    FlightState createAzureContextFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateCloudContextFlight.class,
            azureTestUtils.createAzureContextInputParameters(workspaceUuid, userRequest),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, createAzureContextFlightState.getFlightStatus());
    assertTrue(azureCloudContextService.getAzureCloudContext(workspaceUuid).isPresent());
  }

  public void createResource(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      ControlledResource resource,
      WsmResourceType resourceType)
      throws InterruptedException {
    createResource(workspaceUuid, userRequest, resource, resourceType, null);
  }

  public <T> void createResource(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      ControlledResource resource,
      WsmResourceType resourceType,
      T creationParameters)
      throws InterruptedException {

    FlightState flightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CreateControlledResourceFlight.class,
            azureTestUtils.createControlledResourceInputParameters(
                workspaceUuid, userRequest, resource, creationParameters),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, flightState.getFlightStatus());

    // Verify controlled resource exists in the workspace.
    ControlledResource res =
        controlledResourceService.getControlledResource(workspaceUuid, resource.getResourceId());

    try {
      var castResource = res.castByEnum(resourceType);
      assertTrue(resource.partialEqual(castResource));
    } catch (Exception e) {
      fail(String.format("Failed to cast resource to %s", resourceType), e);
    }
  }

  public <T extends ControlledResource, R> void submitControlledResourceDeletionFlight(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      T controlledResource,
      String azureResourceGroupId,
      String resourceName,
      BiFunction<String, String, R> findResource)
      throws InterruptedException {
    FlightState deleteControlledResourceFlightState =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            DeleteControlledResourcesFlight.class,
            azureTestUtils.deleteControlledResourceInputParameters(
                workspaceUuid, controlledResource.getResourceId(), userRequest, controlledResource),
            STAIRWAY_FLIGHT_TIMEOUT,
            null);

    assertEquals(FlightStatus.SUCCESS, deleteControlledResourceFlightState.getFlightStatus());

    if (findResource != null) {
      Awaitility.await()
          .atMost(1, TimeUnit.MINUTES)
          .pollInterval(5, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                com.azure.core.exception.HttpResponseException exception =
                    assertThrows(
                        com.azure.core.exception.HttpResponseException.class,
                        () -> findResource.apply(azureResourceGroupId, resourceName));
                assertEquals(404, exception.getResponse().getStatusCode());
              });
    }
  }

  public static String getAzureName(String tag) {
    final String id = UUID.randomUUID().toString().substring(0, 6);
    return String.format("wsm-integ-%s-%s", tag, id);
  }
}
