package bio.terra.workspace.connected;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Helper component for providing user access tokens for connected tests. */
@Component
@Profile("connected-test")
public class UserAccessUtils {
  /** The OAuth scopes important for logging in a user and acting on their behalf in GCP. */
  public static final ImmutableList<String> GCP_SCOPES =
      ImmutableList.of(
          "openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");

  /**
   * The path to the service account to use. This service account should be delegated to impersonate
   * users. <a href="https://developers.google.com/admin-sdk/directory/v1/guides/delegation">delegation</a>
   */
  @Value("${workspace.connected-test.user-delegated-service-account-path}")
  public String userDelegatedServiceAccountPath;

  /** The email address of a default user to use for testing. */
  @Value("${workspace.connected-test.default-user-email}")
  private String defaultUserEmail;

  /**
   * The email address of a second, not-the-default user to use for testing. Useful for tests that
   * require two valid users.
   */
  @Value("${workspace.connected-test.second-user-email}")
  private String secondUserEmail;

  @Value("${workspace.connected-test.billing-user-email}")
  private String billingUserEmail;

  /** Email of user with no access to the billing profile */
  @Value("${workspace.connected-test.no-billing-access-user-email}")
  private String noBillingAccessUserEmail;

  // Note: we cannot construct these as finals, since the input emails are not
  // populated at the time of the `new` operation. Instead, we populate in the
  // accessor.
  private TestUser defaultUser = null;
  private TestUser secondUser = null;
  private TestUser billingUser = null;
  private TestUser noBillingUser = null;

  /** Creates a {@link TestUser} for the default test user. */
  public TestUser defaultUser() {
    if (defaultUser == null) {
      defaultUser = new TestUser(defaultUserEmail);
    }
    return defaultUser;
  }
  public TestUser secondUser() {
    if (secondUser == null) {
      secondUser = new TestUser(secondUserEmail);
    }
    return secondUser;
  }
  public TestUser billingUser() {
    if (billingUser == null) {
      billingUser = new TestUser(billingUserEmail);
    }
    return billingUser;
  }
  public TestUser noBillingUser() {
    if (noBillingUser == null) {
      noBillingUser = new TestUser(noBillingAccessUserEmail);
    }
    return noBillingUser;
  }




  /** Creates Google credentials for the user. Relies on domain delegation. */
  public GoogleCredentials generateCredentials(String userEmail) {
    try {
      GoogleCredentials credentials =
          GoogleCredentials.fromStream(new FileInputStream(userDelegatedServiceAccountPath))
              .createScoped(GCP_SCOPES)
              .createDelegated(userEmail);
      credentials.refreshIfExpired();
      return credentials;
    } catch (IOException e) {
      throw new RuntimeException("Error creating GoogleCredentials for user " + userEmail, e);
    }
  }

  // TODO: [PF-2578] remove these direct getters and reference via the TestUser object
  /** Generates an OAuth access token for the userEmail. Relies on domain delegation. */
  public AccessToken generateAccessToken(String userEmail) {
    return generateCredentials(userEmail).getAccessToken();
  }

  /** Generates an OAuth access token for the default test user. */
  public AccessToken defaultUserAccessToken() {
    return defaultUser().getAccessToken();
  }

  /** Generates an OAuth access token for the second test user. */
  public AccessToken secondUserAccessToken() {
    return secondUser().getAccessToken();
  }

  /** Generates an OAuth access token for the billing test user. */
  public AccessToken billingUserAccessToken() {
    return billingUser().getAccessToken();
  }

  /** Expose the default test user email. */
  public String getDefaultUserEmail() {
    return defaultUserEmail;
  }

  /** Expose the second test user email. */
  public String getSecondUserEmail() {
    return secondUserEmail;
  }

  // TODO: [PF-2578] remove these direct getters and reference via the TestUser object
  /** Provides an AuthenticatedUserRequest using the default user's email and access token. */
  public AuthenticatedUserRequest defaultUserAuthRequest() {
    return defaultUser().getAuthenticatedRequest();
  }

  public AuthenticatedUserRequest secondUserAuthRequest() {
    return secondUser().getAuthenticatedRequest();
  }

  public AuthenticatedUserRequest thirdUserAuthRequest() {
    return billingUser().getAuthenticatedRequest();
  }

  public AuthenticatedUserRequest noBillingAccessUserAuthRequest() {
    return noBillingUser().getAuthenticatedRequest();
  }

  /**
   * Represents a test user with convenience method to impersonate them in different forms.
   *
   * <p>This will only work for test user emails that can have delegated credentials.
   */
  public class TestUser {
    private final String email;

    public TestUser(String email) {
      this.email = email;
    }

    public String getEmail() {
      return email;
    }

    public GoogleCredentials getGoogleCredentials() {
      return generateCredentials(email);
    }

    public AccessToken getAccessToken() {
      return generateAccessToken(email);
    }

    public AuthenticatedUserRequest getAuthenticatedRequest() {
      return new AuthenticatedUserRequest()
          .email(email)
          .token(Optional.of(getAccessToken().getTokenValue()));
    }
  }
}
