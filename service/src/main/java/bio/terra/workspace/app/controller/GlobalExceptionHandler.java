package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.workspace.generated.model.ApiErrorReport;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * This module provides a top-level exception handler for controllers. All exceptions that rise
 * through the controllers are caught in this handler. It converts the exceptions into standard
 * ApiErrorReport responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // -- Handle both generic spring 404s and WSM specific 404s
  @ExceptionHandler({NoResourceFoundException.class, NotFoundException.class})
  public ResponseEntity<ApiErrorReport> noResourceFoundHandler() {
    var report = new ApiErrorReport().message("Not found").statusCode(HttpStatus.NOT_FOUND.value());
    return new ResponseEntity<>(report, HttpStatus.NOT_FOUND);
  }

  // -- Error Report - one of our exceptions --
  @ExceptionHandler(ErrorReportException.class)
  public ResponseEntity<ApiErrorReport> errorReportHandler(ErrorReportException ex) {
    return buildApiErrorReport(ex, ex.getStatusCode(), ex.getCauses());
  }

  // -- validation exceptions - we don't control the exception raised
  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    HttpRequestMethodNotSupportedException.class,
    IllegalArgumentException.class,
    NoHandlerFoundException.class
  })
  public ResponseEntity<ApiErrorReport> validationExceptionHandler(Exception ex) {
    logger.error("Global exception handler: catch stack", ex);
    // For security reasons, we generally don't want to include the user's invalid (and potentially
    // malicious) input in the error response, which also means we don't include the full exception.
    // Instead, we return a generic error message about input validation.
    String validationErrorMessage =
        "Request could not be parsed or was invalid: "
            + ex.getClass().getSimpleName()
            + ". Ensure that all types are correct and that enums have valid values.";
    ApiErrorReport errorReport =
        new ApiErrorReport()
            .message(validationErrorMessage)
            .statusCode(HttpStatus.BAD_REQUEST.value());
    return new ResponseEntity<>(errorReport, HttpStatus.BAD_REQUEST);
  }

  /** Give back the fields missing from the request that caused the exception. */
  @ExceptionHandler({MethodArgumentNotValidException.class})
  public ResponseEntity<ApiErrorReport> methodArgNotValidHandler(
      MethodArgumentNotValidException ex) {
    final List<String> errors = new ArrayList<>();
    for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.add(error.getField() + ": " + error.getDefaultMessage());
    }
    for (final ObjectError error : ex.getBindingResult().getGlobalErrors()) {
      errors.add(error.getObjectName() + ": " + error.getDefaultMessage());
    }

    String validationErrorMessage =
        "Request could not be parsed or was invalid: "
            + ex.getClass().getSimpleName()
            + ". Ensure that all types are correct and that enums have valid values.";
    ApiErrorReport errorReport =
        new ApiErrorReport()
            .message(validationErrorMessage)
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .causes(errors);

    return new ResponseEntity<>(errorReport, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorReport> constraintViolationExceptionHandler(
      ConstraintViolationException ex) {
    ApiErrorReport errorReport =
        new ApiErrorReport().message(ex.getMessage()).statusCode(HttpStatus.BAD_REQUEST.value());
    return new ResponseEntity<>(errorReport, HttpStatus.BAD_REQUEST);
  }

  // Exception thrown by Spring Retry code when interrupted.
  @ExceptionHandler({BackOffInterruptedException.class})
  public ResponseEntity<ApiErrorReport> retryBackoffExceptionHandler(
      BackOffInterruptedException ex) {
    String errorMessage =
        "Unexpected interrupt while retrying internal logic. This may succeed on a retry. "
            + ex.getMessage();
    ApiErrorReport errorReport =
        new ApiErrorReport()
            .message(errorMessage)
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    return new ResponseEntity<>(errorReport, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // -- catchall - log so we can understand what we have missed in the handlers above
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorReport> catchallHandler(Exception ex) {
    logger.error("Exception caught by catchall hander", ex);
    return buildApiErrorReport(ex, HttpStatus.INTERNAL_SERVER_ERROR, null);
  }

  private ResponseEntity<ApiErrorReport> buildApiErrorReport(
      Throwable ex, HttpStatus statusCode, List<String> causes) {
    StringBuilder combinedCauseString = new StringBuilder();
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      combinedCauseString.append("cause: ").append(cause).append(", ");
    }
    logger.error("Global exception handler: " + combinedCauseString, ex);
    String message =
        Optional.ofNullable(ex).map(Throwable::getMessage).orElse("no message present");

    ApiErrorReport errorReport =
        new ApiErrorReport().message(message).statusCode(statusCode.value()).causes(causes);
    return new ResponseEntity<>(errorReport, statusCode);
  }
}
