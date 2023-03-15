package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
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
    Root root = jp.readValueAs(Root.class);

    AcceleratorConfig config = new AcceleratorConfig();
    if (root.coreCount != null) {
      config.setCoreCount(root.coreCount);
    }
    if (root.type != null) {
      config.setType(root.type);
    }
    return config;
  }

  private static class Root {
    public Long coreCount;
    public String type;
  }
}
