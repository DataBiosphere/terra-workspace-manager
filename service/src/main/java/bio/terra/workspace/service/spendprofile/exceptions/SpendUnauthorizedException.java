package bio.terra.workspace.service.spendprofile.exceptions;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;

public class SpendUnauthorizedException extends ForbiddenException {
  public SpendUnauthorizedException(String message) {
    super(message);
  }

  /** Factory method for when linking a spend profile is unauthorized. */
  public static SpendUnauthorizedException linkUnauthorized(SpendProfileId spendProfileId) {
    return new SpendUnauthorizedException(
        String.format("User is unauthorized to link spend profile [%s]", spendProfileId.getId()));
  }
}
