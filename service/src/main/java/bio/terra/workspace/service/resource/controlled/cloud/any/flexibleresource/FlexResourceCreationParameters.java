package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class FlexResourceCreationParameters {
  @NotNull private String typeNamespace;
  @NotNull private String type;
  @Nullable private byte[] data;

  public FlexResourceCreationParameters() {}

  @JsonCreator
  public FlexResourceCreationParameters(
      @NotNull @JsonProperty("typeNamespace") String typeNamespace,
      @NotNull @JsonProperty("type") String type,
      @Nullable @JsonProperty("data") byte[] data) {
    this.typeNamespace = typeNamespace;
    this.type = type;
    this.data = data;
  }

  @NotNull
  public String getTypeNamespace() {
    return typeNamespace;
  }

  @NotNull
  public String getType() {
    return type;
  }

  @Nullable
  public byte[] getData() {
    return data;
  }

  public void setTypeNamespace(@NotNull String typeNamespace) {
    this.typeNamespace = typeNamespace;
  }

  public void setType(@NotNull String type) {
    this.type = type;
  }

  public void setData(@Nullable byte[] data) {
    this.data = data;
  }

  public FlexResourceCreationParameters type(String type) {
    this.type = type;
    return this;
  }

  public FlexResourceCreationParameters typeNamespace(String typeNamespace) {
    this.typeNamespace = typeNamespace;
    return this;
  }

  public FlexResourceCreationParameters data(byte[] data) {
    this.data = data;
    return this;
  }
}
