package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.exception.InvalidNameException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** A collection of static validation functions specific to AWS */
@Component
public class AwsResourceValidationUtils {

  /**
   * Validate AWS storage folder name.
   *
   * @param prefixName prefix name
   * @throws InvalidNameException invalid prefix name
   */
  public static void validateAwsStorageFolderName(String prefixName) {
    int nameLength = prefixName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1 || nameLength > 1024) {
      throw new InvalidNameException(
          "storage folder names must contain any sequence of valid Unicode characters, of length 1-1024 bytes when UTF-8 encoded");
    }
  }
}
