package bio.terra.workspace.common.utils;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;

/** Helper to reduce duplicated access token retrieval code. */
public class AuthUtils {
  public static String getAccessToken(
      boolean isAzureControlPlaneEnabled, Collection<String> gcpScopes, Collection<String> azureScopes, String credentialsPath)
      throws IOException {
    if (isAzureControlPlaneEnabled) {
      TokenCredential credential = new DefaultAzureCredentialBuilder().build();
      // The Microsoft Authentication Library (MSAL) currently specifies offline_access, openid,
      // profile, and email by default in authorization and token requests.
      com.azure.core.credential.AccessToken token =
          credential
              .getToken(new TokenRequestContext().addScopes(azureScopes.toArray(new String[azureScopes.size()])))
              .block();
      return token.getToken();
    } else {
      GoogleCredentials creds = null;
      if (credentialsPath == null || credentialsPath.length() == 0) {
        creds = GoogleCredentials.getApplicationDefault().createScoped(gcpScopes);
      } else {
        FileInputStream fileInputStream = new FileInputStream(credentialsPath);
        creds = ServiceAccountCredentials.fromStream(fileInputStream).createScoped(gcpScopes);
      }

      creds.refreshIfExpired();
      AccessToken token = creds.refreshAccessToken();
      return token.getTokenValue();
    }
  }
}
