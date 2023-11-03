package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.common.exception.ValidationException;
import java.util.Set;
import java.util.stream.Collectors;

public class SasPermissionsHelper {
  private static final Set<Character> ALLOWED_SAS_PERMISSIONS =
      Set.of('r', 'l', 'a', 'c', 'w', 'd', 't');

  public static Set<Character> permissionStringToCharSet(String permissions) {
    return permissions.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
  }

  public static void validateSasPermissionString(String permissions) {
    if (permissions == null) {
      return;
    }

    var userPermissionsSet = permissionStringToCharSet(permissions);
    if (!ALLOWED_SAS_PERMISSIONS.containsAll(userPermissionsSet)) {
      throw new ValidationException("Invalid permissions");
    }
  }
}
