package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.workspace.service.resource.WsmResource;

public class ResourceWithFlightId {

  private WsmResource resource;
  private String flightId;

  public ResourceWithFlightId() {
  }

  public ResourceWithFlightId(WsmResource resource, String flightId) {
    this.resource = resource;
    this.flightId = flightId;
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
}
