package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.exceptions.ChildrenBlockingDeletionException;
import bio.terra.workspace.service.workspace.flight.delete.workspace.EnsureNoWorkspaceChildrenStep;
import java.util.List;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.FullyQualifiedResourceId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;

// This doesn't extend BaseUnitTest because we don't need BaseUnitTestMocks
@Tag("unit")
@ActiveProfiles({"unit-test"})
public class EnsureNoWorkspaceChildrenStepTest {

  @Test
  void stepPassesWhenNoChildrenExistForWorkspace() throws Exception {
    var sam = Mockito.mock(SamService.class);
    var workspaceId = UUID.randomUUID();
    var request = new AuthenticatedUserRequest();
    when(sam.getWorkspaceChildResources(request, workspaceId)).thenReturn(List.of());

    var result =
        new EnsureNoWorkspaceChildrenStep(sam, request, workspaceId)
            .doStep(Mockito.mock(FlightContext.class));
    assertEquals(StepStatus.STEP_RESULT_SUCCESS, result.getStepStatus());
  }

  @Test
  void stepFailsWhenChildrenExistForWorkspace() throws Exception {
    var sam = Mockito.mock(SamService.class);
    var workspaceId = UUID.randomUUID();
    var request = new AuthenticatedUserRequest();
    var resourceId = "test_child_resource";
    var resourceType = "test_resource_type";
    var children =
        List.of(
            new FullyQualifiedResourceId().resourceId(resourceId).resourceTypeName(resourceType));
    when(sam.getWorkspaceChildResources(request, workspaceId)).thenReturn(children);

    var e =
        assertThrows(
            ChildrenBlockingDeletionException.class,
            () ->
                new EnsureNoWorkspaceChildrenStep(sam, request, workspaceId)
                    .doStep(Mockito.mock(FlightContext.class)));

    assertTrue(e.getCauses().stream().anyMatch(cause -> cause.contains(resourceId)));
    assertTrue(e.getCauses().stream().anyMatch(cause -> cause.contains(resourceType)));
    assertTrue(e.getStatusCode().is4xxClientError());
  }
}
