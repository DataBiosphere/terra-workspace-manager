package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.workspace.service.resource.model.WsmResource;
import java.util.UUID;

public class ResourceCloneInputs {

  private WsmResource resource;
  private String flightId;
  private UUID destinationResourceId;

  public ResourceCloneInputs() {}

  public ResourceCloneInputs(WsmResource resource, String flightId, UUID destinationResourceId) {
    this.resource = resource;
    this.flightId = flightId;
    this.destinationResourceId = destinationResourceId;
  }

  public WsmResource getResource() {
    return resource;
  }

  public void setResource(WsmResource resource) {
    this.resource = resource;
  }

  public String getFlightId() {
    return flightId;
  }

  public void setFlightId(String flightId) {
    this.flightId = flightId;
  }

  public UUID getDestinationResourceId() {
    return destinationResourceId;
  }

  public void setDestinationResourceId(UUID destinationResourceId) {
    this.destinationResourceId = destinationResourceId;
  }
}
