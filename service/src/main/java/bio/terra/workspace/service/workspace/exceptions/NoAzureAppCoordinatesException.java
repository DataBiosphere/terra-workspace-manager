package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;

public class NoAzureAppCoordinatesException extends BadRequestException {
  public NoAzureAppCoordinatesException(String message) {
    super(message);
  }

  public static NoAzureAppCoordinatesException forSpendProfile(SpendProfileId spendProfileId) {
    return new NoAzureAppCoordinatesException(
        String.format(
            "Azure managed application coordinates required, but none found on spend profile %s",
            spendProfileId.getId()));
  }
}
