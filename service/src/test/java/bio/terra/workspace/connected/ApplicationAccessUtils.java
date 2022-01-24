package bio.terra.workspace.connected;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.auth.oauth2.AccessToken;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("connected-test & app-test")
public class ApplicationAccessUtils {

  @Autowired UserAccessUtils userAccessUtils;

  /** The email address of the application SA to use for testing. */
  @Value("${workspace.application.test-app-sa}")
  private String applicationSaEmail;

  public String getApplicationSaEmail() {
    return applicationSaEmail;
  }

  /** Generates an OAuth access token for the application SA. */
  public AccessToken applicationSaAccessToken() {
    return userAccessUtils.generateAccessToken(applicationSaEmail);
  }

  /** Provides an AuthenticatedUserRequest using the application SA's email and access token. */
  public AuthenticatedUserRequest applicationSaAuthenticatedUserRequest() {
    return new AuthenticatedUserRequest()
        .email(applicationSaEmail)
        .token(Optional.of(applicationSaAccessToken().getTokenValue()));
  }
}
