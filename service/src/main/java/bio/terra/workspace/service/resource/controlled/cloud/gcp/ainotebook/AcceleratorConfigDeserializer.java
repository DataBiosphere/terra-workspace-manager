package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import java.io.IOException;

public class AcceleratorConfigDeserializer extends StdDeserializer<AcceleratorConfig> {

  public AcceleratorConfigDeserializer() {
    this(null);
  }

  public AcceleratorConfigDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public AcceleratorConfig deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException {
    AcceleratorConfig config = new AcceleratorConfig();
    JsonNode node = jp.readValueAsTree();
    if (node.isNull()) {
      return null;
    }
    config.setCoreCount(node.get("coreCount").asLong());
    config.setType(node.get("coreType").asText());
    return config;
  }
}
