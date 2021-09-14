package bio.terra.workspace.service.iam;

import bio.terra.workspace.app.configuration.external.AzureState;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest.AuthType;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxiedAuthenticatedUserRequestFactory implements AuthenticatedUserRequestFactory {

  private static final String BEARER = "Bearer ";
  private static final String BASIC = "Basic ";
  @Autowired AzureState azureState;

  // Method to build an AuthenticatedUserRequest.
  // We try three possible authentication methods. If none succeed, we return
  // an empty AuthenticatedUserRequest object.
  public AuthenticatedUserRequest from(HttpServletRequest servletRequest) {
    return fromOidc(servletRequest)
        .orElse(
            fromBearer(servletRequest)
                .orElse(
                    fromBasic(servletRequest)
                        .orElse(
                            new AuthenticatedUserRequest()
                                .token(Optional.empty())
                                .authType(AuthType.NONE))));
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

  private Optional<AuthenticatedUserRequest> fromBasic(HttpServletRequest servletRequest) {
    // This is only used for the Azure PoC
    if (azureState.isEnabled()) {
      String authHeader = servletRequest.getHeader(AuthHeaderKeys.AUTHORIZATION.getKeyName());
      if (StringUtils.startsWith(authHeader, BASIC)) {
        String encodedInfo = StringUtils.substring(authHeader, BASIC.length());
        String info = new String(Base64.getDecoder().decode(encodedInfo));
        String[] values = StringUtils.split(info, ':');
        return Optional.of(
            new AuthenticatedUserRequest()
                .email(values[0])
                .subjectId(values[1])
                .token(Optional.empty())
                .authType(AuthType.BASIC));
      }
    }

    return Optional.empty();
  }
}
