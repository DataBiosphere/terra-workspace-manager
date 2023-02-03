package bio.terra.workspace.service.grant;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.annotation.Nullable;

public record GrantData(
    UUID grantId,
    UUID workspaceId,
    String userMember,
    String petSaMember,
    GrantType grantType,
    @Nullable UUID resourceId,
    @Nullable String role,
    OffsetDateTime createTime,
    OffsetDateTime expireTime) {}
