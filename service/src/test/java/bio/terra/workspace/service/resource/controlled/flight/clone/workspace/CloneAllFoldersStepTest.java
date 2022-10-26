package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

public class CloneAllFoldersStepTest extends BaseUnitTest {

  @Mock private FlightContext mockFlightContext;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private FolderDao folderDao;
  private CloneAllFoldersStep cloneAllFoldersStep;

  private static UUID SOURCE_WORKSPACE_ID;
  private static final UUID SOURCE_PARENT_FOLDER_ID = UUID.randomUUID();
  private static final UUID SOURCE_SON_FOLDER_ID = UUID.randomUUID();
  private static final String SOURCE_PARENT_FOLDER_NAME = "source-parent-folder-id";
  private static final String SOURCE_PARENT_FOLDER_DESCRIPTION =
      "source parent folder id description";
  private static final String SOURCE_SON_FOLDER_NAME = "source-son-folder-id";
  private static final String SOURCE_SON_FOLDER_DESCRIPTION = "source son folder id description";

  @BeforeEach
  public void setup() throws InterruptedException {

    SOURCE_WORKSPACE_ID = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    folderDao.createFolder(
        new Folder(
            SOURCE_PARENT_FOLDER_ID,
            SOURCE_WORKSPACE_ID,
            SOURCE_PARENT_FOLDER_NAME,
            SOURCE_PARENT_FOLDER_DESCRIPTION,
            /*parentFolderId=*/ null,
            /*properties=*/ null));
    folderDao.createFolder(
        new Folder(
            SOURCE_SON_FOLDER_ID,
            SOURCE_WORKSPACE_ID,
            SOURCE_SON_FOLDER_NAME,
            SOURCE_SON_FOLDER_DESCRIPTION,
            SOURCE_PARENT_FOLDER_ID,
            /*properties=*/ null));

    cloneAllFoldersStep = new CloneAllFoldersStep(folderDao);
  }

  @Test
  public void doStep_foldersCloned() throws InterruptedException {
    var workingMap = new FlightMap();
    var inputParameters = new FlightMap();

    UUID destinationWorkspaceId =
        WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    Workspace destinationWorkspace = workspaceDao.getWorkspace(destinationWorkspaceId);

    inputParameters.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, SOURCE_WORKSPACE_ID);
    inputParameters.put(JobMapKeys.REQUEST.getKeyName(), destinationWorkspace);

    when(mockFlightContext.getInputParameters()).thenReturn(inputParameters);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);

    StepResult stepResult = cloneAllFoldersStep.doStep(mockFlightContext);

    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    assertEquals(
        2,
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).size(),
        "Destination workspace clones the folders successfully");
    assertNotNull(
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).stream()
            .filter(folder -> folder.displayName().equals(SOURCE_PARENT_FOLDER_NAME)),
        "Destination parent folder is cloned successfully");
    Folder destinationParentFolder =
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).stream()
            .filter(folder -> folder.displayName().equals(SOURCE_PARENT_FOLDER_NAME))
            .findFirst()
            .get();
    assertNotNull(
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).stream()
            .filter(folder -> folder.displayName().equals(SOURCE_SON_FOLDER_NAME))
            .findFirst(),
        "Destination son folder is cloned successfully");
    Folder destinationSonFolder =
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).stream()
            .filter(folder -> folder.displayName().equals(SOURCE_SON_FOLDER_NAME))
            .findFirst()
            .get();

    assertNotEquals(
        SOURCE_PARENT_FOLDER_ID,
        destinationParentFolder.id(),
        "Destination parent folder id is generated successfully");
    assertNotEquals(
        SOURCE_SON_FOLDER_ID,
        destinationSonFolder.id(),
        "Destination son folder id is generated successfully");
    assertEquals(
        SOURCE_PARENT_FOLDER_DESCRIPTION,
        destinationParentFolder.description(),
        "Destination parent folder description is cloned successfully");
    assertEquals(
        SOURCE_SON_FOLDER_DESCRIPTION,
        destinationSonFolder.description(),
        "Destination son folder description is cloned successfully");
    assertNull(
        destinationParentFolder.parentFolderId(),
        "Destination parent folder parent id cloned successfully");
    assertEquals(
        destinationParentFolder.id(),
        destinationSonFolder.parentFolderId(),
        "Destination son folder parent id cloned successfully");

    workspaceDao.deleteWorkspace(SOURCE_WORKSPACE_ID);
  }

  @Test
  public void undoStep_foldersCloned() throws InterruptedException {
    var workingMap = new FlightMap();
    var inputParameters = new FlightMap();

    UUID destinationWorkspaceId =
        WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    Workspace destinationWorkspace = workspaceDao.getWorkspace(destinationWorkspaceId);

    inputParameters.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, SOURCE_WORKSPACE_ID);
    inputParameters.put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);
    inputParameters.put(JobMapKeys.REQUEST.getKeyName(), destinationWorkspace);

    when(mockFlightContext.getInputParameters()).thenReturn(inputParameters);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);

    StepResult stepResult = cloneAllFoldersStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    StepResult undoStepResult = cloneAllFoldersStep.undoStep(mockFlightContext);
    assertEquals(undoStepResult.getStepResultSuccess(), stepResult);
    assertEquals(
        0,
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).size(),
        "Destination workspace does not have any folders");

    workspaceDao.deleteWorkspace(SOURCE_WORKSPACE_ID);
  }
}
