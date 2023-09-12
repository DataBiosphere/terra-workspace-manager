package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.generated.model.ApiAzureKubernetesNamespaceAttributes;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.CreateFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.DeleteFederatedCredentialStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetPetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetWorkspaceManagedIdentityStep;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class ControlledAzureKubernetesNamespaceResourceTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightBeanBag mockFlightBeanBag;

  @Test
  void testToApiResource() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var dbCreationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(owner, null);

    var dbResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                dbCreationParameters, workspaceId)
            .build();

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var apiResource = resource.toApiResource();
    assertThat(
        apiResource.getAttributes(),
        equalTo(
            new ApiAzureKubernetesNamespaceAttributes()
                .kubernetesNamespace(resource.getKubernetesNamespace())
                .kubernetesServiceAccount(resource.getKubernetesServiceAccount())
                .managedIdentity(owner)
                .databases(List.of(dbResource.getName()))));
  }

  @Test
  void testAttributesToJson() {
    var workspaceId = UUID.randomUUID();
    var owner = UUID.randomUUID().toString();
    var dbCreationParameters =
        ControlledAzureResourceFixtures.getAzureDatabaseCreationParameters(owner, null);

    var dbResource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureDatabaseResourceBuilder(
                dbCreationParameters, workspaceId)
            .build();

    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            owner, List.of(dbResource.getName()));

    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, workspaceId)
            .build();

    var json = resource.attributesToJson();
    assertThat(
        json,
        equalTo(
            """
                {"kubernetesNamespace":"%s","kubernetesServiceAccount":"%s","managedIdentity":"%s","databases":["%s"]}"""
                .formatted(
                    resource.getKubernetesNamespace(),
                    resource.getKubernetesServiceAccount(),
                    resource.getManagedIdentity(),
                    dbResource.getName())));
  }

  @Test
  void testGetCreateStepsJustNamespace() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());
    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID())
            .build();

    var steps = resource.getCreateSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(List.of(KubernetesNamespaceGuardStep.class, CreateKubernetesNamespaceStep.class)));
  }

  @Test
  void testGetCreateStepsWithPrivateIdentity() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());
    var resource =
        ControlledAzureResourceFixtures
            .makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID(), UUID.randomUUID().toString())
            .build();

    var steps = resource.getCreateSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(
            List.of(
                KubernetesNamespaceGuardStep.class,
                CreateKubernetesNamespaceStep.class,
                GetPetManagedIdentityStep.class,
                GetFederatedIdentityStep.class,
                CreateFederatedIdentityStep.class)));
  }

  @Test
  void testGetCreateStepsWithWorkspaceIdentity() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            UUID.randomUUID().toString(), List.of());
    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID())
            .build();

    var steps = resource.getCreateSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(
            List.of(
                KubernetesNamespaceGuardStep.class,
                CreateKubernetesNamespaceStep.class,
                GetWorkspaceManagedIdentityStep.class,
                GetFederatedIdentityStep.class,
                CreateFederatedIdentityStep.class)));
  }

  @Test
  void testGetCreateStepsWithDatabaseAccess() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            UUID.randomUUID().toString(), List.of(UUID.randomUUID().toString()));
    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID())
            .build();

    var steps = resource.getCreateSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(
            List.of(
                KubernetesNamespaceGuardStep.class,
                CreateKubernetesNamespaceStep.class,
                GetWorkspaceManagedIdentityStep.class,
                GetFederatedIdentityStep.class,
                CreateFederatedIdentityStep.class,
                CreateNamespaceRoleStep.class)));
  }

  @Test
  void testGetDeleteStepsJustNamespace() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());
    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID())
            .build();

    var steps = resource.getDeleteSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(List.of(DeleteKubernetesNamespaceStep.class)));
  }

  @Test
  void testGetDeleteStepsWithPrivateIdentity() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            null, List.of());
    var resource =
        ControlledAzureResourceFixtures
            .makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID(), UUID.randomUUID().toString())
            .build();

    var steps = resource.getDeleteSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(
            List.of(
                DeleteKubernetesNamespaceStep.class,
                GetPetManagedIdentityStep.class,
                GetFederatedIdentityStep.class,
                DeleteFederatedCredentialStep.class)));
  }

  @Test
  void testGetDeleteStepsWithWorkspaceIdentity() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            UUID.randomUUID().toString(), List.of());
    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID())
            .build();

    var steps = resource.getDeleteSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(
            List.of(
                DeleteKubernetesNamespaceStep.class,
                GetWorkspaceManagedIdentityStep.class,
                GetFederatedIdentityStep.class,
                DeleteFederatedCredentialStep.class)));
  }

  @Test
  void testGetDeleteStepsWithDatabaseAccess() {
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureKubernetesNamespaceCreationParameters(
            UUID.randomUUID().toString(), List.of(UUID.randomUUID().toString()));
    var resource =
        ControlledAzureResourceFixtures.makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
                creationParameters, UUID.randomUUID())
            .build();

    var steps = resource.getDeleteSteps(mockFlightBeanBag);
    assertThat(
        steps.stream().map(Object::getClass).toList(),
        equalTo(
            List.of(
                DeleteKubernetesNamespaceStep.class,
                GetWorkspaceManagedIdentityStep.class,
                GetFederatedIdentityStep.class,
                DeleteFederatedCredentialStep.class,
                DeleteNamespaceRoleStep.class)));
  }
}
