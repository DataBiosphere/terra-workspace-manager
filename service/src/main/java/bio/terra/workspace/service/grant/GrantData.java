package bio.terra.workspace.service.grant;

import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;

public record GrantData(
    UUID grantId,
    UUID workspaceId,
    @Nullable String userMember,
    String petSaMember,
    GrantType grantType,
    @Nullable UUID resourceId,
    @Nullable String role,
    Instant createTime,
    Instant expireTime) {}
