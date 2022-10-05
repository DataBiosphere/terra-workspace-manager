package bio.terra.workspace.service.spendprofile.exceptions;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.profile.client.ApiException;
import java.util.Collections;
import org.springframework.http.HttpStatus;

public class BillingProfileManagerServiceAPIException extends ErrorReportException {
  public BillingProfileManagerServiceAPIException(ApiException apiException) {
    super(
        "Error from Billing Profile Manager Service:",
        apiException,
        Collections.singletonList(apiException.getResponseBody()),
        HttpStatus.resolve(apiException.getCode()));
  }
}
