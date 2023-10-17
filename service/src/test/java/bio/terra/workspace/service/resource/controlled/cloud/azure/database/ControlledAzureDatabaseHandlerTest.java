package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("azure-unit")
public class ControlledAzureDatabaseHandlerTest {
  @Test
  void testMakeResourceFromDbForOldRecord() {
    var handler = new ControlledAzureDatabaseHandler();
    final String databaseName = "databaseName";
    final String ownerName = UUID.randomUUID().toString();
    final String namespace = "default";
    var res =
        handler.makeResourceFromDb(
            addRequiredFields(
                new DbResource()
                    .attributes(
                        """
                            {"databaseName":"%s","databaseOwner":"%s","k8sNamespace":"%s","region":"us-central1"}"""
                            .formatted(databaseName, ownerName, namespace))));

    ControlledAzureDatabaseResource actual =
        res.castByEnum(WsmResourceType.CONTROLLED_AZURE_DATABASE);

    assertThat(actual.getDatabaseName(), equalTo(databaseName));
    assertThat(actual.getDatabaseOwner(), equalTo(ownerName));
    assertThat(actual.getAllowAccessForAllWorkspaceUsers(), equalTo(false));
  }

  @Test
  void testMakeResourceFromDb() {
    var handler = new ControlledAzureDatabaseHandler();
    final String databaseName = "databaseName";
    final String ownerName = UUID.randomUUID().toString();
    var res =
        handler.makeResourceFromDb(
            addRequiredFields(
                new DbResource()
                    .attributes(
                        """
                            {"databaseName":"%s","databaseOwner":"%s","allowAccessForAllWorkspaceUsers":true}"""
                            .formatted(databaseName, ownerName))));

    ControlledAzureDatabaseResource actual =
        res.castByEnum(WsmResourceType.CONTROLLED_AZURE_DATABASE);

    assertThat(actual.getDatabaseName(), equalTo(databaseName));
    assertThat(actual.getDatabaseOwner(), equalTo(ownerName));
    assertThat(actual.getAllowAccessForAllWorkspaceUsers(), equalTo(true));
  }

  private DbResource addRequiredFields(DbResource dbResource) {
    return dbResource
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name("name")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .createdByEmail("email")
        .state(WsmResourceState.CREATING)
        .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
        .properties(Map.of())
        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .region("us-central1");
  }
}
