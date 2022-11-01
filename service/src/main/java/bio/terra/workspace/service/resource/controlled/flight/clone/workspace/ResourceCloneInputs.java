package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.workspace.service.resource.model.WsmResource;
import java.util.UUID;
import javax.annotation.Nullable;

public class ResourceCloneInputs {

  private WsmResource resource;
  private String flightId;
  private UUID destinationResourceId;
  private UUID destinationFolderId;

  public ResourceCloneInputs() {}

  public ResourceCloneInputs(
      WsmResource resource,
      String flightId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId) {
    this.resource = resource;
    this.flightId = flightId;
    this.destinationResourceId = destinationResourceId;
    this.destinationFolderId = destinationFolderId;
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

  public UUID getDestinationFolderId() {
    return destinationFolderId;
  }

  public void setDestinationFolderId(@Nullable UUID destinationFolderId) {
    this.destinationFolderId = destinationFolderId;
  }
}
