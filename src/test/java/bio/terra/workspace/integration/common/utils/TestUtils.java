package bio.terra.workspace.integration.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestUtils {
  private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

  @Autowired private ObjectMapper objectMapper;

  public <T> T mapFromJson(String content, Class<T> valueType) throws IOException {
    try {
      return objectMapper.readValue(content, valueType);
    } catch (IOException ex) {
      logger.error(
          "Unable to map JSON response to " + valueType.getName());
      throw ex;
    }
  }

  public String mapToJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      logger.error("Unable to map value to JSON.");
    }
    return null;
  }
}
