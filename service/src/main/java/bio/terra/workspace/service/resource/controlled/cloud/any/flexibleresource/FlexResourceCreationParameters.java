package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import bio.terra.workspace.generated.model.ApiControlledFlexibleResourceCreationParameters;
import javax.annotation.Nullable;

public record FlexResourceCreationParameters(
    String typeNamespace, String type, @Nullable byte[] data) {
  public static FlexResourceCreationParameters fromApiCreationParameters(
      ApiControlledFlexibleResourceCreationParameters apiCreationParameters) {
    return new FlexResourceCreationParameters(
        apiCreationParameters.getTypeNamespace(),
        apiCreationParameters.getType(),
        apiCreationParameters.getData());
  }
}
