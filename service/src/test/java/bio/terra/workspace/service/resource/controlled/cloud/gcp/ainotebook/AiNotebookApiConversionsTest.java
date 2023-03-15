package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;

import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG;

public class AiNotebookApiConversionsTest extends BaseUnitTest {
  private final ApiGcpAiNotebookInstanceAcceleratorConfig defaultApiAcceleratorConfig =
      new ApiGcpAiNotebookInstanceAcceleratorConfig().type("fake-type").coreCount(4L);

  @Test
  public void testToApiAcceleratorConfig() {
    ApiGcpAiNotebookInstanceAcceleratorConfig apiAcceleratorConfig =
        AiNotebookApiConversions.toApiAcceleratorConfig(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG);
    Assertions.assertEquals(2L, apiAcceleratorConfig.getCoreCount());
    Assertions.assertEquals("NVIDIA_TESLA_V100", apiAcceleratorConfig.getType());

    // Test null type.
    ApiGcpAiNotebookInstanceAcceleratorConfig configWithNullType =
        AiNotebookApiConversions.toApiAcceleratorConfig(
            new AcceleratorConfig().setType("fake-type-null-count"));

    Assertions.assertNull(configWithNullType.getCoreCount());
    Assertions.assertEquals("fake-type-null-count", configWithNullType.getType());

    // Test null count.
    ApiGcpAiNotebookInstanceAcceleratorConfig configWithNullCount =
        AiNotebookApiConversions.toApiAcceleratorConfig(new AcceleratorConfig().setCoreCount(1L));

    Assertions.assertEquals(1L, configWithNullCount.getCoreCount());
    Assertions.assertNull(configWithNullCount.getType());
  }

  @Test
  public void testFromApiAcceleratorConfig() {
    AcceleratorConfig resultAcceleratorConfig =
        AiNotebookApiConversions.fromApiAcceleratorConfig(defaultApiAcceleratorConfig);

    Assertions.assertEquals(4L, resultAcceleratorConfig.getCoreCount());
    Assertions.assertEquals("fake-type", resultAcceleratorConfig.getType());

    // Test null type.
    AcceleratorConfig configWithNullType =
        AiNotebookApiConversions.fromApiAcceleratorConfig(
            new ApiGcpAiNotebookInstanceAcceleratorConfig().type("fake-type-null-count"));

    Assertions.assertNull(configWithNullType.getCoreCount());
    Assertions.assertEquals("fake-type-null-count", configWithNullType.getType());

    // Test null count.
    AcceleratorConfig configWithNullCount =
        AiNotebookApiConversions.fromApiAcceleratorConfig(
            new ApiGcpAiNotebookInstanceAcceleratorConfig().coreCount(1L));

    Assertions.assertEquals(1L, configWithNullCount.getCoreCount());
    Assertions.assertNull(configWithNullCount.getType());
  }

  @Test
  public void testInverse() {
    // Start with API: API -> GCP -> API.
    ApiGcpAiNotebookInstanceAcceleratorConfig apiComposition =
        AiNotebookApiConversions.toApiAcceleratorConfig(
            AiNotebookApiConversions.fromApiAcceleratorConfig(defaultApiAcceleratorConfig));
    Assertions.assertEquals(defaultApiAcceleratorConfig, apiComposition);

    // Start with GCP: GCP -> API -> GCP.
    AcceleratorConfig gcpComposition =
        AiNotebookApiConversions.fromApiAcceleratorConfig(
            AiNotebookApiConversions.toApiAcceleratorConfig(
                DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG));
    Assertions.assertEquals(DEFAULT_AI_NOTEBOOK_ACCELERATOR_CONFIG, gcpComposition);
  }
}
