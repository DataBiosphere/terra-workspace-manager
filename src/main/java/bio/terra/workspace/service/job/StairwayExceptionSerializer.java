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

public class StairwayExceptionSerializer implements ExceptionSerializer {
  private ObjectMapper objectMapper;

  public StairwayExceptionSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String serialize(Exception exception) {
    if (exception == null) {
      return StringUtils.EMPTY;
    }

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
          .setErrorReportException(true)
          .setErrorDetails(((ErrorReportException) exception).getCauses());
    } else {
      fields.setErrorReportException(false);
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

    // If this is an ErrorReport exception and the exception exposes a constructor with the
    // error details, then we try to use that.
    if (fields.isErrorReportException()) {
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

    // We have either an ErrorReport exception that doesn't support error details or some other runtime
    // exception
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
