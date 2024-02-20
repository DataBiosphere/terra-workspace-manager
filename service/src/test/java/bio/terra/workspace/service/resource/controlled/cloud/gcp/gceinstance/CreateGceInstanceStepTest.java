package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceGuestAccelerator;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class CreateGceInstanceStepTest extends BaseSpringBootUnitTest {

  private static final List<String> SA_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");
  private static final String WORKSPACE_ID = "my-workspce-ufid";
  private static final String SERVER_ID = "test-server";

  @Test
  public void setFields() {
    var creationParameters =
        new ApiGcpGceInstanceCreationParameters()
            .machineType("machine-type")
            .metadata(Map.of("metadata-key", "metadata-value"))
            .addGuestAcceleratorsItem(
                new ApiGcpGceInstanceGuestAccelerator().cardCount(1).type("accelerator-type"))
            .vmImage("project-id/image-family/image-name");
    Instance instance =
        CreateGceInstanceStep.setFields(
            creationParameters,
            "instance-name",
            "project-id",
            "zone",
            "foo@bar.com",
            WORKSPACE_ID,
            SERVER_ID,
            new Instance(),
            "main");
    Map<String, String> metadata =
        instance.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    assertThat(metadata, Matchers.aMapWithSize(3));
    assertThat(metadata, Matchers.hasEntry("metadata-key", "metadata-value"));
    assertDefaultMetadata(instance);
    assertEquals("foo@bar.com", instance.getServiceAccounts().get(0).getEmail());
    assertEquals(SA_SCOPES, instance.getServiceAccounts().get(0).getScopes());
    assertEquals(1, instance.getGuestAccelerators().size());
    assertEquals(
        "projects/project-id/zones/zone/acceleratorTypes/accelerator-type",
        instance.getGuestAccelerators().get(0).getAcceleratorType());
    assertEquals(
        "project-id/image-family/image-name",
        instance.getDisks().get(0).getInitializeParams().getSourceImage());
  }

  @Test
  public void setFieldsNoFields() {
    var localBranch = "monkey";
    Instance instance =
        CreateGceInstanceStep.setFields(
            new ApiGcpGceInstanceCreationParameters(),
            "instance-name",
            "project-id",
            "zone",
            "foo@bar.com",
            WORKSPACE_ID,
            SERVER_ID,
            new Instance(),
            localBranch);
    Map<String, String> metadata =
        instance.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    assertThat(metadata, Matchers.aMapWithSize(2));
    assertDefaultMetadata(instance);
    assertEquals("foo@bar.com", instance.getServiceAccounts().get(0).getEmail());
    assertEquals(SA_SCOPES, instance.getServiceAccounts().get(0).getScopes());
  }

  private void assertDefaultMetadata(Instance instance) {
    Map<String, String> metadata =
        instance.getMetadata().getItems().stream()
            .collect(Collectors.toMap(Items::getKey, Items::getValue));
    assertThat(metadata, Matchers.hasEntry("terra-workspace-id", WORKSPACE_ID));
    assertThat(metadata, Matchers.hasEntry("terra-cli-server", SERVER_ID));
  }

  @Test
  public void setFieldsThrowsForReservedMetadataKeys() {
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateGceInstanceStep.setFields(
                new ApiGcpGceInstanceCreationParameters()
                    // "terra-workspace-id" is a reserved metadata key.
                    .metadata(Map.of("terra-workspace-id", "fakeworkspaceid")),
                "instance-name",
                "project-id",
                "zone",
                "foo@bar.com",
                "workspaceId",
                "server-id",
                new Instance(),
                "main"));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateGceInstanceStep.setFields(
                new ApiGcpGceInstanceCreationParameters()
                    // "terra-cli-server" is a reserved metadata key.
                    .metadata(Map.of("terra-cli-server", "fakeserver")),
                "isntance-name",
                "project-id",
                "zone",
                "foo@bar.com",
                "workspaceId",
                "server-id",
                new Instance(),
                "main"));
  }
}
