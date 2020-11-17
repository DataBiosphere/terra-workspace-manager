package bio.terra.workspace.connected;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Helper component for providing user access tokens for connected test. */
@Component
@Profile("connected-test")
public class UserAccessUtils {
  /** The OAuth scopes important for logging in a user. */
  private static final ImmutableList<String> LOGIN_SCOPES =
      ImmutableList.of("openid", "email", "profile");

  /**
   * The path to the service account to use. This service account should be delegated to impersonate
   * users. https://developers.google.com/admin-sdk/directory/v1/guides/delegation
   */
  @Value("${workspace.connected-test.user-delegated-service-account-path}")
  private String userDelegatedServiceAccountPath;

  /** The email address of a default user to use for testing. */
  @Value("${workspace.connected-test.default-user-email}")
  private String defaultUserEmail;

  /**
   * The email address of a second, not-the-default user to use for testing. Useful for tests that
   * require two valid users.
   */
  @Value("${workspace.connected-test.second-user-email}")
  private String secondUserEmail;

  //    /** Provides {@link GoogleCredentials} that can be used to impersonate users. */
  //    @Lazy
  //    @Bean
  //    public GoogleCredentials userDelegatedCredentials() throws IOException {
  //        GoogleCredentials.fromStream(new FileInputStream(userDelegatedServiceAccountPath))
  //        .createScoped();
  //        GoogleCredentials credentials =
  //                GoogleCredentials.fromStream(
  //                        new
  // ByteArrayInputStream(Files.readAllBytes(serviceAccountFile.toPath())))
  //    }

  /** Generates an OAuth access token for the userEmail. Relies on domain delegation. */
  public AccessToken generateAccessToken(String userEmail) throws IOException {
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(new FileInputStream(userDelegatedServiceAccountPath))
            .createScoped(LOGIN_SCOPES)
            .createDelegated(userEmail);
    credentials.refreshIfExpired();
    return credentials.getAccessToken();
  }

  /** Generates an OAuth access token for the default test user. */
  public AccessToken defaultUserAccessToken() {
    try {
      return generateAccessToken(defaultUserEmail);
    } catch (IOException e) {
      throw new RuntimeException(
          "Error creating default user access token for user " + defaultUserEmail, e);
    }
  }

  /** Generates an OAuth access token for the second test user. */
  public AccessToken secondUserAccessToken() {
    try {
      return generateAccessToken(secondUserEmail);
    } catch (IOException e) {
      throw new RuntimeException(
          "Error creating secibd user access token for user " + secondUserEmail, e);
    }
  }
}
