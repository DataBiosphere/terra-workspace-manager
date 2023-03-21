package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AiNotebookApiConversionsTest extends BaseUnitTest {
  private final ApiGcpAiNotebookInstanceAcceleratorConfig defaultApiAcceleratorConfig =
      new ApiGcpAiNotebookInstanceAcceleratorConfig().type("fake-type").coreCount(4L);

  @Test
  public void toApiAcceleratorConfig() {
    ApiGcpAiNotebookInstanceAcceleratorConfig apiAcceleratorConfig =
        AcceleratorConfig.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG);
    Assertions.assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG.coreCount(), apiAcceleratorConfig.getCoreCount());
    Assertions.assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG.type(), apiAcceleratorConfig.getType());
  }

  @Test
  public void fromApiAcceleratorConfig() {
    AcceleratorConfig resultAcceleratorConfig =
        AcceleratorConfig.fromApiAcceleratorConfig(defaultApiAcceleratorConfig);

    Assertions.assertEquals(
        defaultApiAcceleratorConfig.getCoreCount(), resultAcceleratorConfig.coreCount());
    Assertions.assertEquals(defaultApiAcceleratorConfig.getType(), resultAcceleratorConfig.type());
  }

  @Test
  public void testInverse() {
    // Start with API: API -> GCP -> API.
    ApiGcpAiNotebookInstanceAcceleratorConfig apiComposition =
        AcceleratorConfig.toApiAcceleratorConfig(
            AcceleratorConfig.fromApiAcceleratorConfig(defaultApiAcceleratorConfig));
    Assertions.assertEquals(defaultApiAcceleratorConfig, apiComposition);

    // Start with GCP: GCP -> API -> GCP.
    AcceleratorConfig gcpComposition =
        AcceleratorConfig.fromApiAcceleratorConfig(
            AcceleratorConfig.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG));
    Assertions.assertEquals(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG, gcpComposition);
  }
}
