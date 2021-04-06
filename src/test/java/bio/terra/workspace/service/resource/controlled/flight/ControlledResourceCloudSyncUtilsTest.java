package bio.terra.workspace.service.resource.controlled.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.controlled.flight.create.ControlledResourceCloudSyncUtils;
import bio.terra.workspace.service.resource.controlled.mappings.ControlledResourceInheritanceMapping;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.mappings.CustomGcpIamRoleMapping;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ControlledResourceCloudSyncUtilsTest extends BaseUnitTest {

  private FlightMap workingMap;
  private final String projectId = "myFakeProjectId";

  @BeforeEach
  public void setup() {
    workingMap = new FlightMap();
    workingMap.put(WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL, "fakeReaderGroup");
    workingMap.put(WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL, "fakeWriterGroup");
    workingMap.put(WorkspaceFlightMapKeys.IAM_APPLICATION_GROUP_EMAIL, "fakeAppGroup");
    workingMap.put(WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL, "fakeOwnerGroup");
  }

  @Test
  public void userSharedResourceFillsPolicy() {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .build();
    Policy emptyPolicy = Policy.newBuilder().build();

    assertTrue(emptyPolicy.getBindingsList().isEmpty());
    Policy modifiedPolicy =
        ControlledResourceCloudSyncUtils.updatePolicyWithSamGroups(
            resource, projectId, emptyPolicy, workingMap);
    assertFalse(modifiedPolicy.getBindingsList().isEmpty());

    // There should be exactly one binding for each entry of the IAM inheritance mapping.

    int expectedNumBindings =
        ControlledResourceInheritanceMapping.getInheritanceMapping(
                AccessScopeType.ACCESS_SCOPE_SHARED, ManagedByType.MANAGED_BY_USER)
            .size();
    assertThat(modifiedPolicy.getBindingsList().size(), equalTo(expectedNumBindings));
    // All Bindings should be for our pre-defined custom roles.
    assertCustomRoleNames(modifiedPolicy);
  }

  @Test
  public void userPrivateResourceFillsPolicy() {
    String resourceOwnerName = "fakeOwnerName";
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .assignedUser(resourceOwnerName)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .build();
    workingMap.put(ControlledResourceKeys.IAM_RESOURCE_READER_GROUP_EMAIL, "resourceReaderGroup");
    workingMap.put(ControlledResourceKeys.IAM_RESOURCE_WRITER_GROUP_EMAIL, "resourceWriterGroup");
    workingMap.put(ControlledResourceKeys.IAM_RESOURCE_EDITOR_GROUP_EMAIL, "resourceEditorGroup");

    Policy emptyPolicy = Policy.newBuilder().build();

    assertTrue(emptyPolicy.getBindingsList().isEmpty());
    Policy modifiedPolicy =
        ControlledResourceCloudSyncUtils.updatePolicyWithSamGroups(
            resource, projectId, emptyPolicy, workingMap);
    assertFalse(modifiedPolicy.getBindingsList().isEmpty());

    // There should be exactly one binding for each entry of the IAM inheritance mapping, plus
    // one binding for each of the READER, WRITER, and EDITOR synced resource-level roles.
    int expectedNumBindings =
        ControlledResourceInheritanceMapping.getInheritanceMapping(
                    AccessScopeType.ACCESS_SCOPE_PRIVATE, ManagedByType.MANAGED_BY_USER)
                .size()
            + 3;
    assertThat(modifiedPolicy.getBindingsList().size(), equalTo(expectedNumBindings));
    // All Bindings should be for our pre-defined custom roles.
    assertCustomRoleNames(modifiedPolicy);
  }

  /** Assert that every binding in a Policy is one of WSM's pre-defined custom IAM roles. */
  private void assertCustomRoleNames(Policy policy) {
    List<String> allowedRoleNames =
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES.values().stream()
            .map(CustomGcpIamRole::getRoleName)
            .map(roleName -> String.format("projects/%s/roles/%s", projectId, roleName))
            .collect(Collectors.toList());
    List<String> providedRoleNames =
        policy.getBindingsList().stream().map(Binding::getRole).collect(Collectors.toList());
    assertThat(providedRoleNames, everyItem(in(allowedRoleNames)));
  }
}
