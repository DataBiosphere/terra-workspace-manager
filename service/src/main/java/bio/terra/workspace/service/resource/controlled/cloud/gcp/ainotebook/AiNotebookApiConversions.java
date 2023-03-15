package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;

import javax.annotation.Nullable;

/** Utility method for converting AI notebook objects between WSM formats and GCP formats. */
public class AiNotebookApiConversions {
  private AiNotebookApiConversions() {}

  /** Convert from GCP accelerator config to API object. */
  public static ApiGcpAiNotebookInstanceAcceleratorConfig toApiAcceleratorConfig(
      @Nullable AcceleratorConfig acceleratorConfig) {
    if (acceleratorConfig == null) {
      return null;
    }
    return new ApiGcpAiNotebookInstanceAcceleratorConfig()
        .type(acceleratorConfig.getType())
        .coreCount(acceleratorConfig.getCoreCount());
  }

  /** Convert from API object to GCP accelerator config. */
  public static AcceleratorConfig fromApiAcceleratorConfig(
      @Nullable ApiGcpAiNotebookInstanceAcceleratorConfig ApiAcceleratorConfig) {
    if (ApiAcceleratorConfig == null) {
      return null;
    }
    return new AcceleratorConfig()
        .setType(ApiAcceleratorConfig.getType())
        .setCoreCount(ApiAcceleratorConfig.getCoreCount());
  }
}
