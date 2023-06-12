package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import java.util.UUID;

public record ResourceDeleteFlightPair(UUID resourceId, String flightId) {}
