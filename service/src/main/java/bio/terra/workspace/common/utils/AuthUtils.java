package bio.terra.workspace.common.utils;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Collection;

/** Helper to reduce duplicated access token retrieval code. */
public class AuthUtils {
  public static String GetAccessToken(boolean isAzureControlPlaneEnabled, Collection<String> scopes)
      throws IOException {
    if (isAzureControlPlaneEnabled) {
      TokenCredential credential = new DefaultAzureCredentialBuilder().build();
      // The Microsoft Authentication Library (MSAL) currently specifies offline_access, openid,
      // profile, and email by default in authorization and token requests.
      com.azure.core.credential.AccessToken token =
          credential
              .getToken(new TokenRequestContext().addScopes("https://graph.microsoft.com/.default"))
              .block();
      return token.getToken();
    } else {
      GoogleCredentials creds = GoogleCredentials.getApplicationDefault().createScoped(scopes);
      creds.refreshIfExpired();
      AccessToken token = creds.refreshAccessToken();
      return token.getTokenValue();
    }
  }
}
