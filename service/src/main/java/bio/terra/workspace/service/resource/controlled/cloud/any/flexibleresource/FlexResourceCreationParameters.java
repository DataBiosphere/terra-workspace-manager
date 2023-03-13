package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import bio.terra.workspace.generated.model.ApiControlledFlexibleResourceCreationParameters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public record FlexResourceCreationParameters(
    String typeNamespace, String type, @Nullable byte[] data) {

  @JsonCreator
  public FlexResourceCreationParameters(
      @JsonProperty("typeNamespace") String typeNamespace,
      @JsonProperty("type") String type,
      @Nullable @JsonProperty("data") byte[] data) {
    this.typeNamespace = typeNamespace;
    this.type = type;
    this.data = data;
  }

  public static FlexResourceCreationParameters fromApiCreationParameters(
      ApiControlledFlexibleResourceCreationParameters apiCreationParameters) {
    return new FlexResourceCreationParameters(
        apiCreationParameters.getTypeNamespace(),
        apiCreationParameters.getType(),
        apiCreationParameters.getData());
  }
}
