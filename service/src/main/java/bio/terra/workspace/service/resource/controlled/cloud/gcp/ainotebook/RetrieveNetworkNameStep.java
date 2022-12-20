package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_LOCATION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_NETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SUBNETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.SubnetworkList;
import com.google.api.services.compute.model.Zone;
import com.google.api.services.compute.model.ZoneList;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/**
 * A {@link Step} for retrieving the network and subnetwork to use for the AI notebook instance from
 * Google.
 */
public class RetrieveNetworkNameStep implements Step {

  private final CrlService crlService;
  private final ControlledAiNotebookInstanceResource resource;
  private final GcpCloudContextService gcpCloudContextService;

  public RetrieveNetworkNameStep(
      CrlService crlService,
      ControlledAiNotebookInstanceResource resource,
      GcpCloudContextService gcpCloudContextService) {
    this.crlService = crlService;
    this.resource = resource;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    CloudComputeCow compute = crlService.getCloudComputeCow();
    SubnetworkList subnetworks;
    try {
      String location = maybeGetValidZone(projectId);
      flightContext.getWorkingMap().put(CREATE_NOTEBOOK_LOCATION, location);
      String region = getRegionForNotebook(projectId, location);
      flightContext.getWorkingMap().put(CREATE_RESOURCE_REGION, region);
      subnetworks = compute.subnetworks().list(projectId, region).execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    pickAndStoreNetwork(subnetworks, flightContext.getWorkingMap());
    return StepResult.getStepResultSuccess();
  }

  /**
   * Fetches the valid zone given resource location. If none is found, returns the Ai notebook
   * location attributes.
   */
  private String maybeGetValidZone(String projectId) throws IOException {
    String location = resource.getLocation();
    ZoneList zoneList = crlService.getCloudComputeCow().zones().list(projectId).execute();

    return zoneList.getItems().stream()
        .filter(zone -> extractNameFromUrl(zone.getRegion()).equalsIgnoreCase(location))
        .map(zone -> zone.getName())
        .sorted()
        .findAny()
        .orElse(location);
  }

  private String getRegionForNotebook(String projectId, String location) throws IOException {
    try {
      // GCP is a little loose with its zone/location naming. An AI notebook location has the
      // same id as a GCE zone. Use the location to look up the zone.
      Zone zone = crlService.getCloudComputeCow().zones().get(projectId, location).execute();
      return extractNameFromUrl(zone.getRegion());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
        // Throw a better error message if the location isn't known.
        throw new BadRequestException(String.format("Unsupported location '%s'", location));
      }
      throw e;
    }
  }

  private void pickAndStoreNetwork(SubnetworkList subnetworks, FlightMap workingMap) {
    if (subnetworks.getItems().isEmpty()) {
      throw new BadRequestException(
          String.format("No subnetworks available for location '%s'", resource.getLocation()));
    }
    // Arbitrarily grab the first subnetwork. We don't have a use case for multiple subnetworks or
    // them mattering yet, so use any available subnetwork.
    Subnetwork subnetwork = subnetworks.getItems().get(0);
    workingMap.put(CREATE_NOTEBOOK_NETWORK_NAME, extractNameFromUrl(subnetwork.getNetwork()));
    workingMap.put(CREATE_NOTEBOOK_SUBNETWORK_NAME, subnetwork.getName());
  }

  /**
   * Extract the name from a network URL like
   * "https://www.googleapis.com/compute/v1/projects/{PROJECT_ID}/global/networks/{NAME}" or route
   * URL like "https://www.googleapis.com/compute/v1/projects/{PROJECT_ID}/regions/{REGION_NAME}"
   */
  private static String extractNameFromUrl(String url) {
    int lastSlashIndex = url.lastIndexOf('/');
    if (lastSlashIndex == -1) {
      throw new InternalServerErrorException(
          String.format("Unable to extract resource name from '%s'", url));
    }
    return url.substring(lastSlashIndex + 1);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // This is a read-only step, so nothing needs to be undone.
    return StepResult.getStepResultSuccess();
  }
}
