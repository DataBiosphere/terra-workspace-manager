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
  public void testToApiAcceleratorConfig() {
    ApiGcpAiNotebookInstanceAcceleratorConfig apiAcceleratorConfig =
        AcceleratorConfig.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG);
    Assertions.assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG.coreCount(), apiAcceleratorConfig.getCoreCount());
    Assertions.assertEquals(
        DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG.type(), apiAcceleratorConfig.getType());

    // Test null type.
    ApiGcpAiNotebookInstanceAcceleratorConfig configWithNullType =
        AcceleratorConfig.toApiAcceleratorConfig(
            new AcceleratorConfig("fake-type-null-count", null));

    Assertions.assertNull(configWithNullType.getCoreCount());
    Assertions.assertEquals("fake-type-null-count", configWithNullType.getType());

    // Test null count.
    ApiGcpAiNotebookInstanceAcceleratorConfig configWithNullCount =
        AcceleratorConfig.toApiAcceleratorConfig(new AcceleratorConfig(null, 1L));

    Assertions.assertEquals(1L, configWithNullCount.getCoreCount());
    Assertions.assertNull(configWithNullCount.getType());
  }

  @Test
  public void testFromApiAcceleratorConfig() {
    AcceleratorConfig resultAcceleratorConfig =
        AcceleratorConfig.fromApiAcceleratorConfig(defaultApiAcceleratorConfig);

    Assertions.assertEquals(
        defaultApiAcceleratorConfig.getCoreCount(), resultAcceleratorConfig.coreCount());
    Assertions.assertEquals(defaultApiAcceleratorConfig.getType(), resultAcceleratorConfig.type());

    // Test null type.
    AcceleratorConfig configWithNullType =
        AcceleratorConfig.fromApiAcceleratorConfig(
            new ApiGcpAiNotebookInstanceAcceleratorConfig().type("fake-type-null-count"));

    Assertions.assertNull(configWithNullType.coreCount());
    Assertions.assertEquals("fake-type-null-count", configWithNullType.type());

    // Test null count.
    AcceleratorConfig configWithNullCount =
        AcceleratorConfig.fromApiAcceleratorConfig(
            new ApiGcpAiNotebookInstanceAcceleratorConfig().coreCount(1L));

    Assertions.assertEquals(1L, configWithNullCount.coreCount());
    Assertions.assertNull(configWithNullCount.type());
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
