package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;

/**
 * Exception for an operation using a Spend Profile when a billing account is required and the Spend
 * Profile does not have a billing account associated with it.
 */
public class NoBillingAccountException extends BadRequestException {
  public NoBillingAccountException(String message) {
    super(message);
  }

  public static NoBillingAccountException forSpendProfile(SpendProfileId spendProfileId) {
    return new NoBillingAccountException(
        String.format(
            "Billing account id required, but none found on spend profile %s",
            spendProfileId.getId()));
  }
}
