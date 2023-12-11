package bio.terra.workspace.service.iam;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest.AuthType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ProxiedAuthenticatedUserRequestFactory implements AuthenticatedUserRequestFactory {
  private static final String BEARER = "Bearer ";

  // Method to build an AuthenticatedUserRequest.
  // We try three possible authentication methods. If none succeed, we return
  // an empty AuthenticatedUserRequest object.
  public AuthenticatedUserRequest from(HttpServletRequest servletRequest) {
    return fromOidc(servletRequest)
        .orElse(
            fromBearer(servletRequest)
                .orElse(
                    new AuthenticatedUserRequest()
                        .token(Optional.empty())
                        .authType(AuthType.NONE)));
  }

  private Optional<AuthenticatedUserRequest> fromOidc(HttpServletRequest servletRequest) {
    Optional<String> token =
        Optional.ofNullable(
            servletRequest.getHeader(AuthHeaderKeys.OIDC_ACCESS_TOKEN.getKeyName()));
    if (token.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new AuthenticatedUserRequest()
            .email(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName()))
            .subjectId(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_USER_ID.getKeyName()))
            .token(token)
            .authType(AuthType.OIDC));
  }

  private Optional<AuthenticatedUserRequest> fromBearer(HttpServletRequest servletRequest) {
    String authHeader = servletRequest.getHeader(AuthHeaderKeys.AUTHORIZATION.getKeyName());
    if (StringUtils.startsWith(authHeader, BEARER)) {
      return Optional.of(
          new AuthenticatedUserRequest()
              .email(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName()))
              .subjectId(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_USER_ID.getKeyName()))
              .token(Optional.of(StringUtils.substring(authHeader, BEARER.length())))
              .authType(AuthType.BEARER));
    }

    return Optional.empty();
  }
}
