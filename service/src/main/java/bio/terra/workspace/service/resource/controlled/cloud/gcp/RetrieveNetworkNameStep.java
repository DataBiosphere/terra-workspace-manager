package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_LOCATION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_NETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_SUBNETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.landingzone.stairway.flight.utils.FlightUtils;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster.ControlledDataprocClusterResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.SubnetworkList;
import com.google.api.services.compute.model.Zone;
import com.google.api.services.compute.model.ZoneList;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/**
 * A {@link Step} for retrieving the network and subnetwork to use for the GCE instance from Google.
 */
public class RetrieveNetworkNameStep implements Step {

  private final CrlService crlService;
  private final ControlledResource resource;
  private final GcpCloudContextService gcpCloudContextService;

  public RetrieveNetworkNameStep(
      CrlService crlService,
      ControlledResource resource,
      GcpCloudContextService gcpCloudContextService) {
    this.crlService = crlService;
    this.resource = resource;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    CloudComputeCow compute = crlService.getCloudComputeCow();
    String projectId =
        switch (resource.getResourceType()) {
          case CONTROLLED_GCP_GCE_INSTANCE -> {
            ControlledGceInstanceResource gceInstance =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
            yield gceInstance.getProjectId();
          }
          case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> {
            ControlledAiNotebookInstanceResource aiNotebookInstance =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
            yield aiNotebookInstance.getProjectId();
          }
          case CONTROLLED_GCP_DATAPROC_CLUSTER -> {
            ControlledDataprocClusterResource dataprocCluster =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);
            yield dataprocCluster.getProjectId();
          }
          default -> throw new InternalLogicException("Bad resource type passed to step.");
        };
    SubnetworkList subnetworks;
    try {
      String region;
      // If the resource is a dataproc cluster, we use the region in the resource. Otherwise, we
      // compute the region from the zone.
      if (resource.getResourceType().equals(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER)) {
        region = resource.getRegion();
      } else {
        String zone = maybeGetValidZone(projectId);
        flightContext.getWorkingMap().put(CREATE_GCE_INSTANCE_LOCATION, zone);
        region = getRegionForInstance(projectId, zone);
      }
      flightContext.getWorkingMap().put(CREATE_RESOURCE_REGION, region);
      subnetworks = compute.subnetworks().list(projectId, region).execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    pickAndStoreNetwork(subnetworks, flightContext.getWorkingMap());
    return StepResult.getStepResultSuccess();
  }

  /**
   * Fetches the valid zone given resource "zone", since the default workspace region might be
   * passed in if no zone is specified. If none is found, returns the resource zone attributes.
   */
  private String maybeGetValidZone(String projectId) throws IOException {
    String location =
        switch (resource.getResourceType()) {
          case CONTROLLED_GCP_GCE_INSTANCE -> {
            ControlledGceInstanceResource gceInstance =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
            yield gceInstance.getZone();
          }
          case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> {
            ControlledAiNotebookInstanceResource aiNotebookInstance =
                resource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
            yield aiNotebookInstance.getLocation();
          }
          default -> throw new InternalLogicException("Bad resource type passed to step.");
        };
    ZoneList zoneList = crlService.getCloudComputeCow().zones().list(projectId).execute();

    return zoneList.getItems().stream()
        .filter(zone -> extractNameFromUrl(zone.getRegion()).equalsIgnoreCase(location))
        .map(Zone::getName)
        .sorted()
        .findAny()
        .orElse(location);
  }

  private String getRegionForInstance(String projectId, String zoneName) throws IOException {
    try {
      Zone zone = crlService.getCloudComputeCow().zones().get(projectId, zoneName).execute();
      return extractNameFromUrl(zone.getRegion());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
        // Throw a better error message if the location isn't known.
        throw new BadRequestException(String.format("Unsupported zone '%s'", zoneName));
      }
      throw e;
    }
  }

  private void pickAndStoreNetwork(SubnetworkList subnetworks, FlightMap workingMap) {
    if (subnetworks.getItems() == null || subnetworks.getItems().isEmpty()) {
      throw new BadRequestException(
          String.format(
              "No subnetworks available for zone '%s'",
              FlightUtils.getRequired(workingMap, CREATE_GCE_INSTANCE_LOCATION, String.class)));
    }
    // Arbitrarily grab the first subnetwork. We don't have a use case for multiple subnetworks or
    // them mattering yet, so use any available subnetwork.
    Subnetwork subnetwork = subnetworks.getItems().get(0);
    workingMap.put(CREATE_GCE_INSTANCE_NETWORK_NAME, extractNameFromUrl(subnetwork.getNetwork()));
    workingMap.put(CREATE_GCE_INSTANCE_SUBNETWORK_NAME, subnetwork.getName());
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
