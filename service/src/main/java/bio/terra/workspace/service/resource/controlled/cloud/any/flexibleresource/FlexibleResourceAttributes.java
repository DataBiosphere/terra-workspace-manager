package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FlexibleResourceAttributes {
  private final String typeNamespace;
  private final String type;
  private final String data;

  @JsonCreator
  public FlexibleResourceAttributes(
      @JsonProperty("typeNamespace") String typeNamespace,
      @JsonProperty("type") String type,
      @JsonProperty("data") String data) {
    this.typeNamespace = typeNamespace;
    this.type = type;
    this.data = data;
  }

  public String getTypeNamespace() {
    return typeNamespace;
  }

  public String getType() {
    return type;
  }

  public String getData() {
    return data;
  }
}
