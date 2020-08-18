package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.exception.MDCHandlingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("mdcUtils")
public class MDCUtils {

  @Autowired ObjectMapper objectMapper;
  private final TypeReference<Map<String, String>> mapType =
      new TypeReference<Map<String, String>>() {};

  public String serializeMdc(Map<String, String> mdcMap) {
    try {
      return objectMapper.writeValueAsString(mdcMap);
    } catch (JsonProcessingException e) {
      throw new MDCHandlingException("Error serializing MDC map from string: " + mdcMap.toString());
    }
  }

  public String serializeCurrentMdc() {
    return serializeMdc(MDC.getCopyOfContextMap());
  }

  public Map<String, String> deserializeMdcString(String serializedMdc) {
    Map<String, String> mdcContextMap = null;
    try {
      return objectMapper.readValue(serializedMdc, mapType);
    } catch (JsonProcessingException e) {
      throw new MDCHandlingException("Error deserializing MDC map from string: " + serializedMdc);
    }
  }
}
