package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

public class CloneAllFoldersStepTest extends BaseSpringBootUnitTest {

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
  private static final String DEFAULT_USER_EMAIL = "foo@gmail.com";

  @BeforeEach
  public void setup() {

    SOURCE_WORKSPACE_ID = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    folderDao.createFolder(
        new Folder(
            SOURCE_PARENT_FOLDER_ID,
            SOURCE_WORKSPACE_ID,
            SOURCE_PARENT_FOLDER_NAME,
            SOURCE_PARENT_FOLDER_DESCRIPTION,
            /* parentFolderId= */ null,
            /* properties= */ Map.of("foo", "bar"),
            DEFAULT_USER_EMAIL,
            /* createdDate= */ null));
    folderDao.createFolder(
        new Folder(
            SOURCE_SON_FOLDER_ID,
            SOURCE_WORKSPACE_ID,
            SOURCE_SON_FOLDER_NAME,
            SOURCE_SON_FOLDER_DESCRIPTION,
            SOURCE_PARENT_FOLDER_ID,
            /* properties= */ Map.of("fooSon", "barSon"),
            DEFAULT_USER_EMAIL,
            /* createdDate= */ null));

    cloneAllFoldersStep = new CloneAllFoldersStep(mockSamService(), folderDao);
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(DEFAULT_USER_EMAIL);
  }

  @Test
  public void doStep_foldersCloned() throws InterruptedException {
    var workingMap = new FlightMap();
    var inputParameters = new FlightMap();

    UUID destinationWorkspaceId =
        WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    inputParameters.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, SOURCE_WORKSPACE_ID);
    inputParameters.put(WORKSPACE_ID, destinationWorkspaceId);

    when(mockFlightContext.getInputParameters()).thenReturn(inputParameters);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);

    StepResult stepResult = cloneAllFoldersStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    List<Folder> sourceFolders = folderDao.listFoldersInWorkspace(SOURCE_WORKSPACE_ID);
    List<Folder> destinationFolders = folderDao.listFoldersInWorkspace(destinationWorkspaceId);
    Folder destinationParentFolder =
        destinationFolders.stream()
            .filter(folder -> folder.displayName().equals(SOURCE_PARENT_FOLDER_NAME))
            .collect(onlyElement());
    Folder destinationSonFolder =
        destinationFolders.stream()
            .filter(folder -> folder.displayName().equals(SOURCE_SON_FOLDER_NAME))
            .collect(onlyElement());

    assertThat(
        destinationFolders, containsInAnyOrder(destinationParentFolder, destinationSonFolder));
    assertThat(
        sourceFolders.stream().map(Folder::description).collect(Collectors.toList()),
        containsInAnyOrder(
            destinationParentFolder.description(), destinationSonFolder.description()));
    assertThat(
        sourceFolders.stream().map(Folder::properties).collect(Collectors.toList()),
        containsInAnyOrder(
            destinationParentFolder.properties(), destinationSonFolder.properties()));
    assertNull(destinationParentFolder.parentFolderId());
    assertEquals(destinationParentFolder.id(), destinationSonFolder.parentFolderId());
  }

  @Test
  public void undoStep_foldersCloned() throws InterruptedException {
    var workingMap = new FlightMap();
    var inputParameters = new FlightMap();

    UUID destinationWorkspaceId =
        WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);
    inputParameters.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, SOURCE_WORKSPACE_ID);
    inputParameters.put(WORKSPACE_ID, destinationWorkspaceId);

    when(mockFlightContext.getInputParameters()).thenReturn(inputParameters);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);

    StepResult stepResult = cloneAllFoldersStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    StepResult undoStepResult = cloneAllFoldersStep.undoStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), undoStepResult);
    assertTrue(
        folderDao.listFoldersInWorkspace(destinationWorkspaceId).isEmpty(),
        "Destination workspace does not have any folders");
  }

  @AfterEach
  public void clean_up() {
    WorkspaceFixtures.deleteWorkspaceFromDb(SOURCE_WORKSPACE_ID, workspaceDao);
  }
}
