package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import com.google.api.services.notebooks.v1.model.Instance;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class CreateAiNotebookInstanceStepTest extends BaseUnitTest {
  @Test
  public void setFields() {
    var creationParameters =
        new ApiGcpAiNotebookInstanceCreationParameters()
            .postStartupScript("script.sh")
            .machineType("machine-type")
            .installGpuDriver(true)
            .customGpuDriverPath("custom-path")
            .bootDiskType("boot-disk-type")
            .bootDiskSizeGb(111L)
            .dataDiskType("data-disk-type")
            .dataDiskSizeGb(222L)
            .metadata(Map.of("metadata-key", "metadata-value"))
            .acceleratorConfig(
                new ApiGcpAiNotebookInstanceAcceleratorConfig()
                    .coreCount(4L)
                    .type("accelerator-type"))
            .vmImage(
                new ApiGcpAiNotebookInstanceVmImage()
                    .projectId("project-id")
                    .imageFamily("image-family")
                    .imageName("image-name"))
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("repository").tag("tag"));

    Instance instance =
        CreateAiNotebookInstanceStep.setFields(creationParameters, "foo@bar.com", new Instance());
    assertEquals("script.sh", instance.getPostStartupScript());
    assertEquals(true, instance.getInstallGpuDriver());
    assertEquals("custom-path", instance.getCustomGpuDriverPath());
    assertEquals("boot-disk-type", instance.getBootDiskType());
    assertEquals(111L, instance.getBootDiskSizeGb());
    assertEquals("data-disk-type", instance.getDataDiskType());
    assertEquals(222L, instance.getDataDiskSizeGb());
    assertThat(instance.getMetadata(), Matchers.aMapWithSize(2));
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_account"));
    assertThat(instance.getMetadata(), Matchers.hasEntry("metadata-key", "metadata-value"));
    assertEquals("foo@bar.com", instance.getServiceAccount());
    assertEquals(4L, instance.getAcceleratorConfig().getCoreCount());
    assertEquals("accelerator-type", instance.getAcceleratorConfig().getType());
    assertEquals("project-id", instance.getVmImage().getProject());
    assertEquals("image-family", instance.getVmImage().getImageFamily());
    assertEquals("image-name", instance.getVmImage().getImageName());
    assertEquals("repository", instance.getContainerImage().getRepository());
    assertEquals("tag", instance.getContainerImage().getTag());
  }

  @Test
  public void setFieldsNoFields() {
    Instance instance =
        CreateAiNotebookInstanceStep.setFields(
            new ApiGcpAiNotebookInstanceCreationParameters(), "foo@bar.com", new Instance());
    assertThat(instance.getMetadata(), Matchers.aMapWithSize(1));
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_account"));
    assertEquals("foo@bar.com", instance.getServiceAccount());
  }

  @Test
  public void setFieldsThrowsForReservedMetadataKeys() {
    assertThrows(
        BadRequestException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "proxy-mode" is a reserved metadata key.
                    .metadata(Map.of("proxy-mode", "mail")),
                "foo@bar.com",
                new Instance()));
  }
}
