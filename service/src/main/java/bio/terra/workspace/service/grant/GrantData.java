package bio.terra.workspace.service.grant;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GrantData(
  UUID grantId,
  UUID workspaceId,
  String userMember,
  String petSaMember,
  GrantType grantType,
  @Nullable UUID resourceId,
  @Nullable String role,
  OffsetDateTime createTime,
  OffsetDateTime expireTime) {
}
