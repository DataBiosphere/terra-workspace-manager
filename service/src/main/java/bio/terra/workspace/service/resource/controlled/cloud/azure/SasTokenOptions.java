package bio.terra.workspace.service.resource.controlled.cloud.azure;

import java.time.OffsetDateTime;

public record SasTokenOptions(
    String ipRange,
    OffsetDateTime startTime,
    OffsetDateTime expiryTime,
    String blobName,
    String permissions,
    Boolean enableProxy) {}
