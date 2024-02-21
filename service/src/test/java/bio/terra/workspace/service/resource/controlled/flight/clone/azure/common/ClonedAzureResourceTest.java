package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("azureUnit")
public class ClonedAzureResourceTest extends BaseAzureSpringBootUnitTest {

  static UUID sourceWorkspaceId = UUID.randomUUID();
  static UUID sourceResourceId = UUID.randomUUID();
  static UUID destinationWorkspaceId = UUID.randomUUID();
  static ControlledAzureManagedIdentityResource newMIResource =
      ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
              ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters(),
              destinationWorkspaceId)
          .managedIdentityName("idfoobar")
          .build();
  static ControlledAzureStorageContainerResource newContainerResource =
      ControlledAzureResourceFixtures.makeDefaultAzureStorageContainerResourceBuilder(
              destinationWorkspaceId)
          .storageContainerName("container-baz")
          .build();

  @Test
  void clonedAzureResource_defaultConstructor() {
    var clone =
        new ClonedAzureResource(
            CloningInstructions.COPY_DEFINITION,
            sourceWorkspaceId,
            sourceResourceId,
            newContainerResource);
    assertEquals(CloningInstructions.COPY_DEFINITION, clone.effectiveCloningInstructions());
    assertEquals(sourceWorkspaceId, clone.sourceWorkspaceId());
    assertEquals(sourceResourceId, clone.sourceResourceId());
    assertEquals(newContainerResource, clone.resource());
  }

  @Test
  void clonedAzureResource_emptyResourceConstructor() {
    var clone =
        new ClonedAzureResource(
            CloningInstructions.COPY_DEFINITION, sourceWorkspaceId, sourceResourceId);
    assertEquals(CloningInstructions.COPY_DEFINITION, clone.effectiveCloningInstructions());
    assertEquals(sourceWorkspaceId, clone.sourceWorkspaceId());
    assertEquals(sourceResourceId, clone.sourceResourceId());
    assertNull(clone.resource());
  }

  @Test
  void clonedAzureResource_storageContainer() {
    var clone =
        new ClonedAzureResource(
            CloningInstructions.COPY_DEFINITION,
            sourceWorkspaceId,
            sourceResourceId,
            newContainerResource);
    assertEquals(CloningInstructions.COPY_DEFINITION, clone.effectiveCloningInstructions());
    assertEquals(sourceWorkspaceId, clone.sourceWorkspaceId());
    assertEquals(sourceResourceId, clone.sourceResourceId());
    assertEquals(newContainerResource, clone.storageContainer());
    assertThrows(BadRequestException.class, clone::managedIdentity);
  }

  @Test
  void clonedAzureResource_managedIdentity() {
    var clone =
        new ClonedAzureResource(
            CloningInstructions.COPY_DEFINITION,
            sourceWorkspaceId,
            sourceResourceId,
            newMIResource);
    assertEquals(CloningInstructions.COPY_DEFINITION, clone.effectiveCloningInstructions());
    assertEquals(sourceWorkspaceId, clone.sourceWorkspaceId());
    assertEquals(sourceResourceId, clone.sourceResourceId());
    assertEquals(newMIResource, clone.managedIdentity());
    assertThrows(BadRequestException.class, clone::storageContainer);
  }
}
