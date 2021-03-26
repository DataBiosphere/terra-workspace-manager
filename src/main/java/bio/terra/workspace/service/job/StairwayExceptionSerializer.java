package bio.terra.workspace.service.job;

import bio.terra.stairway.ExceptionSerializer;
import bio.terra.workspace.common.exception.ErrorReportException;
import bio.terra.workspace.service.job.exception.ExceptionSerializerException;
import bio.terra.workspace.service.job.exception.JobResponseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

public class StairwayExceptionSerializer implements ExceptionSerializer {
  private final ObjectMapper objectMapper;

  public StairwayExceptionSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String serialize(Exception rawException) {
    if (rawException == null) {
      return StringUtils.EMPTY;
    }

    Exception exception = rawException;

    // Wrap non-runtime exceptions so they can be rethrown later
    if (!(exception instanceof RuntimeException)) {
      exception = new JobResponseException(exception.getMessage(), exception);
    }

    StairwayExceptionFields fields =
        new StairwayExceptionFields()
            .setClassName(exception.getClass().getName())
            .setMessage(exception.getMessage());

    if (exception instanceof ErrorReportException) {
      fields
          .setApiErrorReportException(true)
          .setErrorDetails(((ErrorReportException) exception).getCauses())
          .setErrorCode(((ErrorReportException) exception).getStatusCode().value());
    } else {
      fields.setApiErrorReportException(false);
    }

    try {
      return objectMapper.writeValueAsString(fields);
    } catch (JsonProcessingException ex) {
      // The StairwayExceptionFields object is a very simple POJO and should never cause
      // JSON processing to fail.
      throw new ExceptionSerializerException("This should never happen", ex);
    }
  }

  @Override
  public Exception deserialize(String serializedException) {
    if (StringUtils.isEmpty(serializedException)) {
      return null;
    }

    // Decode the exception fields from JSON
    StairwayExceptionFields fields;
    try {
      fields = objectMapper.readValue(serializedException, StairwayExceptionFields.class);
    } catch (IOException ex) {
      // objectMapper exceptions
      return new ExceptionSerializerException(
          "Failed to deserialize exception data: " + serializedException, ex);
    }

    // Find the class from the class name
    Class<?> clazz;
    try {
      clazz = Class.forName(fields.getClassName());
    } catch (ClassNotFoundException ex) {
      return new ExceptionSerializerException(
          "Exception class not found: "
              + fields.getClassName()
              + "; Exception message: "
              + fields.getMessage());
    }

    // If this is an ApiErrorReport exception and the exception exposes a constructor with the
    // error details, then we try to use that. We first try a version with a message, causes, and
    // status code.
    if (fields.isApiErrorReportException()) {
      try {
        Constructor<?> ctor = clazz.getConstructor(String.class, List.class, HttpStatus.class);
        Object object =
            ctor.newInstance(
                fields.getMessage(),
                fields.getErrorDetails(),
                HttpStatus.valueOf(fields.getErrorCode()));
        return (Exception) object;
      } catch (NoSuchMethodException
          | SecurityException
          | InstantiationException
          | IllegalAccessException
          | IllegalArgumentException
          | InvocationTargetException ex) {
        // We didn't find a constructor with error these error details or construction failed.
      }
    }

    // If this is an ApiErrorReport exception but didn't match the above constructor signature, we
    // try again with another common pattern of message + causes.
    if (fields.isApiErrorReportException()) {
      try {
        Constructor<?> ctor = clazz.getConstructor(String.class, List.class);
        Object object = ctor.newInstance(fields.getMessage(), fields.getErrorDetails());
        return (Exception) object;
      } catch (NoSuchMethodException
          | SecurityException
          | InstantiationException
          | IllegalAccessException
          | IllegalArgumentException
          | InvocationTargetException ex) {
        // We didn't find a constructor with error details or construction failed. Fall through
      }
    }

    // We have either an ApiErrorReport exception that doesn't support error details or some other
    // runtime exception
    try {
      Constructor<?> ctor = clazz.getConstructor(String.class);
      Object object = ctor.newInstance(fields.getMessage());
      return (Exception) object;
    } catch (NoSuchMethodException
        | SecurityException
        | InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException ex) {
      // We didn't find a vanilla constructor or construction failed. Fall through
    }

    return new ExceptionSerializerException(
        "Failed to construct exception: "
            + fields.getClassName()
            + "; Exception message: "
            + fields.getMessage());
  }
}
