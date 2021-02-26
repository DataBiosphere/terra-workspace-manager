package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.SerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DbUtil {
  private final ObjectMapper persistenceObjectMapper;

  @Autowired
  public DbUtil(ObjectMapper persistenceObjectMapper) {
    this.persistenceObjectMapper = persistenceObjectMapper;
  }

  String toJsonFromProperties(Map<String, String> kvmap) {
    try {
      return persistenceObjectMapper.writeValueAsString(kvmap);
    } catch (JsonProcessingException ex) {
      throw new SerializationException("Failed to serialize properties", ex);
    }
  }

  Map<String, String> toPropertiesFromJson(String json) {
    try {
      return persistenceObjectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException ex) {
      throw new SerializationException("Failed to deserialize properties", ex);
    }
  }
}
