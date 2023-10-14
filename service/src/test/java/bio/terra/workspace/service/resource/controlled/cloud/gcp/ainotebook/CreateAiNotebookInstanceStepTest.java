package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.CreateAiNotebookInstanceStep.DEFAULT_POST_STARTUP_SCRIPT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class CreateAiNotebookInstanceStepTest extends BaseUnitTest {

  private static final List<String> SA_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");
  private static final String WORKSPACE_ID = "my-workspce-ufid";

  private static final UUID RESOURCE_ID = UUID.randomUUID();

  private static final String APP_PROXY = "app-proxy.com";
  private static final String SERVER_ID = "test-server";
  private static final String NOTEBOOK_DISABLE_ROOT_KEY = "notebook-disable-root";

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
        CreateAiNotebookInstanceStep.setFields(
            creationParameters,
            "foo@bar.com",
            WORKSPACE_ID,
            RESOURCE_ID,
            SERVER_ID,
            new Instance(),
            "main",
            Optional.of(APP_PROXY));
    assertEquals("script.sh", instance.getPostStartupScript());
    assertTrue(instance.getInstallGpuDriver());
    assertEquals("custom-path", instance.getCustomGpuDriverPath());
    assertEquals("boot-disk-type", instance.getBootDiskType());
    assertEquals(111L, instance.getBootDiskSizeGb());
    assertEquals("data-disk-type", instance.getDataDiskType());
    assertEquals(222L, instance.getDataDiskSizeGb());
    assertThat(instance.getMetadata(), Matchers.aMapWithSize(7));
    assertThat(instance.getMetadata(), Matchers.hasEntry("metadata-key", "metadata-value"));
    assertThat(instance.getMetadata(), Matchers.hasEntry(NOTEBOOK_DISABLE_ROOT_KEY, "true"));
    assertDefaultMetadata(instance);
    assertEquals("foo@bar.com", instance.getServiceAccount());
    assertEquals(SA_SCOPES, instance.getServiceAccountScopes());
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
    var localBranch = "monkey";
    Instance instance =
        CreateAiNotebookInstanceStep.setFields(
            new ApiGcpAiNotebookInstanceCreationParameters(),
            "foo@bar.com",
            WORKSPACE_ID,
            RESOURCE_ID,
            SERVER_ID,
            new Instance(),
            localBranch,
            Optional.of(APP_PROXY));
    assertThat(instance.getMetadata(), Matchers.aMapWithSize(5));
    assertDefaultMetadata(instance);
    assertEquals("foo@bar.com", instance.getServiceAccount());
    assertEquals(SA_SCOPES, instance.getServiceAccountScopes());
    assertEquals(
        String.format(DEFAULT_POST_STARTUP_SCRIPT, localBranch), instance.getPostStartupScript());
    assertFalse(instance.getMetadata().containsKey(NOTEBOOK_DISABLE_ROOT_KEY));
  }

  private void assertDefaultMetadata(Instance instance) {
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-workspace-id", WORKSPACE_ID));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-cli-server", SERVER_ID));
    assertThat(
        instance.getMetadata(), Matchers.hasEntry("terra-resource-id", RESOURCE_ID.toString()));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-app-proxy", APP_PROXY));
  }

  @Test
  public void setFields_userSpecifiedNotebookDisableRootToFalse() {
    var creationParameters =
        new ApiGcpAiNotebookInstanceCreationParameters()
            .metadata(Map.of(NOTEBOOK_DISABLE_ROOT_KEY, "false"))
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("repository").tag("tag"));

    Instance instance =
        CreateAiNotebookInstanceStep.setFields(
            creationParameters,
            "foo@bar.com",
            WORKSPACE_ID,
            RESOURCE_ID,
            SERVER_ID,
            new Instance(),
            "main",
            /*appProxyUrl=*/ Optional.empty());
    assertThat(instance.getMetadata(), Matchers.hasEntry(NOTEBOOK_DISABLE_ROOT_KEY, "false"));
  }

  @Test
  public void setFieldsThrowsForReservedMetadataKeys() {
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "terra-workspace-id" is a reserved metadata key.
                    .metadata(Map.of("terra-workspace-id", "fakeworkspaceid")),
                "foo@bar.com",
                "workspaceId",
                RESOURCE_ID,
                "server-id",
                new Instance(),
                "main",
                /*appProxyUrl=*/ Optional.empty()));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "terra-cli-server" is a reserved metadata key.
                    .metadata(Map.of("terra-cli-server", "fakeserver")),
                "foo@bar.com",
                "workspaceId",
                RESOURCE_ID,
                "server-id",
                new Instance(),
                "main",
                /*appProxyUrl=*/ Optional.empty()));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "proxy-mode" is a reserved metadata key.
                    .metadata(Map.of("proxy-mode", "mail")),
                "foo@bar.com",
                "workspaceId",
                RESOURCE_ID,
                "server-id",
                new Instance(),
                "main",
                /*appProxyUrl=*/ Optional.empty()));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "terra-workspace-id" is a reserved metadata key.
                    .metadata(Map.of("terra-app-proxy", "random-proxy")),
                "foo@bar.com",
                "workspaceId",
                RESOURCE_ID,
                "server-id",
                new Instance(),
                "main",
                /*appProxyUrl=*/ Optional.empty()));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "terra-workspace-id" is a reserved metadata key.
                    .metadata(Map.of("terra-resource-id", "fakerid")),
                "foo@bar.com",
                "workspaceId",
                RESOURCE_ID,
                "server-id",
                new Instance(),
                "main",
                /*appProxyUrl=*/ Optional.empty()));
  }
}
