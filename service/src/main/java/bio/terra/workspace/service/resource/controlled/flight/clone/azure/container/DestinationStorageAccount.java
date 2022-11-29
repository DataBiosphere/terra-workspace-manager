package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import java.util.UUID;
import javax.annotation.Nullable;

public record DestinationStorageAccount(boolean isLandingZone, @Nullable UUID resourceId) {}
