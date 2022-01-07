package bio.terra.workspace.service.workspace.model;

import bio.terra.stairway.FlightState;
import bio.terra.workspace.service.resource.WsmResource;
import java.util.Optional;

public class EnumeratedJob {
  private FlightState flightState;
  private String jobDescription;
  private OperationType operationType;
  private WsmResource resource;

  public FlightState getFlightState() {
    return flightState;
  }

  public EnumeratedJob flightState(FlightState flightState) {
    this.flightState = flightState;
    return this;
  }

  public String getJobDescription() {
    return jobDescription;
  }

  public EnumeratedJob jobDescription(String jobDescription) {
    this.jobDescription = jobDescription;
    return this;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public EnumeratedJob operationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public Optional<WsmResource> getResource() {
    return Optional.ofNullable(resource);
  }

  public EnumeratedJob resource(WsmResource resource) {
    this.resource = resource;
    return this;
  }
}
