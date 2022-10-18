package bio.terra.workspace.service.workspace.flight.cloudcontext.gcp;

import static bio.terra.workspace.service.workspace.CloudSyncRoleMapping.CUSTOM_GCP_IAM_ROLES;
import static bio.terra.workspace.unit.WorkspaceUnitTestUtils.PROJECT_ID;
import static bio.terra.workspace.unit.WorkspaceUnitTestUtils.createWorkspaceWithGcpContext;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.IamCow.Projects;
import bio.terra.cloudres.google.iam.IamCow.Projects.Roles;
import bio.terra.cloudres.google.iam.IamCow.Projects.Roles.Get;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRoleMapping;
import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.testing.json.MockJsonFactory;
import com.google.api.services.iam.v1.model.Role;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(Lifecycle.PER_CLASS)
public class RetrieveGcpIamCustomRoleStepTest extends BaseUnitTest {

  private final FlightMap workingMap = new FlightMap();

  @Autowired private WorkspaceDao workspaceDao;
  @Mock private IamCow iamCow;
  @Mock private Projects projects;
  @Mock private Roles roles;
  @Mock private Get getProject;
  @Mock private FlightContext flightContext;

  private RetrieveGcpIamCustomRoleStep retrieveGcpIamCustomRoleStep;

  @BeforeAll
  public void setUp() throws IOException {
    createWorkspaceWithGcpContext(workspaceDao);
    retrieveGcpIamCustomRoleStep = new RetrieveGcpIamCustomRoleStep(iamCow, PROJECT_ID);
    when(flightContext.getWorkingMap()).thenReturn(workingMap);
    when(iamCow.projects()).thenReturn(projects);
    when(projects.roles()).thenReturn(roles);
    when(roles.get(anyString())).thenReturn(getProject);
    when(getProject.execute()).thenReturn(new Role());
  }

  @Test
  public void doStep() throws InterruptedException {
    retrieveGcpIamCustomRoleStep.doStep(flightContext);

    HashSet<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();
    customGcpIamRoles.addAll(CUSTOM_GCP_IAM_ROLES);
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
    for (CustomGcpIamRole customGcpIamRole : customGcpIamRoles) {
      assertNotNull(
          workingMap.get(customGcpIamRole.getFullyQualifiedRoleName(PROJECT_ID), Role.class));
    }
  }

  @Test
  public void doStep_oneOfTheRoleIsNotFound_notAddedToWorkingMap()
      throws IOException, InterruptedException {
    CustomGcpIamRole customGcpIamRole = CustomGcpIamRole.of("PROJECT_OWNER", List.of());
    JsonFactory jsonFactory = new MockJsonFactory();
    when(roles.get(customGcpIamRole.getFullyQualifiedRoleName(PROJECT_ID)))
        .thenThrow(
            GoogleJsonResponseExceptionFactoryTesting.newMock(
                jsonFactory, HttpStatus.SC_NOT_FOUND, "Test Exception"));

    retrieveGcpIamCustomRoleStep.doStep(flightContext);

    HashSet<CustomGcpIamRole> customGcpIamRoles = new HashSet<>();
    customGcpIamRoles.addAll(CUSTOM_GCP_IAM_ROLES);
    customGcpIamRoles.addAll(CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES.values());
    for (CustomGcpIamRole iamRole : customGcpIamRoles) {
      if ("PROJECT_OWNER".equals(iamRole.getRoleName())) {
        assertFalse(workingMap.containsKey(iamRole.getFullyQualifiedRoleName(PROJECT_ID)));
      } else {
        assertNotNull(workingMap.get(iamRole.getFullyQualifiedRoleName(PROJECT_ID), Role.class));
      }
    }
  }
}
