package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

/**
 * Internal record for the AcceleratorConfig of AI notebooks {@link
 * com.google.api.services.notebooks.v1.model.AcceleratorConfig}
 *
 * @param type Type of this accelerator. The value must not be {@code null}.
 * @param coreCount Count of cores of this accelerator. The value must not be {@code null}.
 */
public record AcceleratorConfig(@NotNull String type, @NotNull Long coreCount) {

  /** Convert from GCP accelerator config to API object. */
  public static ApiGcpAiNotebookInstanceAcceleratorConfig toApiAcceleratorConfig(
      @Nullable AcceleratorConfig acceleratorConfig) {
    if (acceleratorConfig == null) {
      return null;
    }
    return new ApiGcpAiNotebookInstanceAcceleratorConfig()
        .type(acceleratorConfig.type)
        .coreCount(acceleratorConfig.coreCount);
  }

  /** Convert from API object to GCP accelerator config. */
  public static AcceleratorConfig fromApiAcceleratorConfig(
      @Nullable ApiGcpAiNotebookInstanceAcceleratorConfig ApiAcceleratorConfig) {
    if (ApiAcceleratorConfig == null) {
      return null;
    }
    return new AcceleratorConfig(
        ApiAcceleratorConfig.getType(), ApiAcceleratorConfig.getCoreCount());
  }
}
