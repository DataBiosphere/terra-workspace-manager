package bio.terra.workspace.integration.common.auth;

import bio.terra.workspace.integration.common.configuration.TestConfiguration;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthService {

  private final TestConfiguration testConfig;
  private File serviceAccountFile;
  private final List<String> userLoginScopes = Arrays.asList("openid", "email", "profile");

  @Autowired
  public AuthService(TestConfiguration testConfig) {
    this.testConfig = testConfig;
    Optional<String> serviceAccountFilePath =
        Optional.ofNullable(this.testConfig.getServiceAccountFilePath());
    serviceAccountFilePath.ifPresent(s -> serviceAccountFile = new File(s));
  }

  public String getAuthToken(String userEmail) throws IOException {
    // todo: revisit the need for a cache and implement Caffeine as needed
    return getAccessToken(userEmail);
  }

  private String getAccessToken(String userEmail) throws IOException {
    if (!Optional.ofNullable(serviceAccountFile).isPresent()) {
      throw new IllegalStateException(
          String.format(
              "Service account file not found: %s", testConfig.getServiceAccountFilePath()));
    }
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(
                new ByteArrayInputStream(Files.readAllBytes(serviceAccountFile.toPath())))
            .createScoped(userLoginScopes)
            .createDelegated(userEmail);
    credentials.refreshIfExpired();
    AccessToken newAccessToken = credentials.getAccessToken();
    return newAccessToken.getTokenValue();
  }
}
