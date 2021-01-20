package bio.terra.workspace.service.spendprofile.exceptions;

import bio.terra.workspace.common.exception.ForbiddenException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;

public class SpendUnauthorizedException extends ForbiddenException {
  public SpendUnauthorizedException(String message) {
    super(message);
  }

  /** Factory method for when linking a spend profile is unauthorized. */
  public static SpendUnauthorizedException linkUnauthorized(SpendProfileId spendProfileId) {
    return new SpendUnauthorizedException(
        String.format("User is unauthorized to link spend profile [%s]", spendProfileId.id()));
  }
}
