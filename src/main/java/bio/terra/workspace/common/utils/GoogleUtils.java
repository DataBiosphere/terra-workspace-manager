package bio.terra.workspace.common.utils;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class GoogleUtils {

  public static GoogleCredentials getGoogleCredentials(
      String serviceAccountFilePath, List<String> scopes) throws IOException {
    return GoogleCredentials.fromStream(
            new ByteArrayInputStream(Files.readAllBytes(new File(serviceAccountFilePath).toPath())))
        .createScoped(scopes);
  }
}
