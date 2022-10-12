package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FolderKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class CloneAllFoldersStepTest extends BaseUnitTest {

  @MockBean private FlightContext mockFlightContext;
  @MockBean private FolderDao mockFolderDao;
  private CloneAllFoldersStep cloneAllFoldersStep;

  private static final UUID SOURCE_WORKSPACE_ID = UUID.randomUUID();
  private static final UUID DESTINATION_WORKSPACE_ID = UUID.randomUUID();
  private static final String DESTINATION_WORKSPACE_USERFACING_ID =
      "destination-workspace-userfacing-id";
  private static final UUID SOURCE_PARENT_FOLDER_ID = UUID.randomUUID();
  private static final UUID SOURCE_SON_FOLDER_ID = UUID.randomUUID();
  private static final String SOURCE_PARENT_FOLDER_NAME = "source-parent-folder-id";
  private static final String SOURCE_PARENT_FOLDER_DESCRIPTION =
      "source parent folder id description";
  private static final String SOURCE_SON_FOLDER_NAME = "source-son-folder-id";
  private static final String SOURCE_SON_FOLDER_DESCRIPTION = "source son folder id description";

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockFolderDao.listFoldersInWorkspace(eq(SOURCE_WORKSPACE_ID)))
        .thenReturn(
            ImmutableList.of(
                new Folder(
                    SOURCE_PARENT_FOLDER_ID,
                    SOURCE_WORKSPACE_ID,
                    SOURCE_PARENT_FOLDER_NAME,
                    SOURCE_PARENT_FOLDER_DESCRIPTION,
                    /*parentFolderId=*/ null,
                    /*properties=*/ null),
                new Folder(
                    SOURCE_SON_FOLDER_ID,
                    SOURCE_WORKSPACE_ID,
                    SOURCE_SON_FOLDER_NAME,
                    SOURCE_SON_FOLDER_DESCRIPTION,
                    SOURCE_PARENT_FOLDER_ID,
                    /*properties=*/ null)));
    cloneAllFoldersStep = new CloneAllFoldersStep(mockFolderDao);
  }

  @Test
  public void doStep_foldersCloned() throws InterruptedException {
    var inputParameters = new FlightMap();
    var workingMap = new FlightMap();
    var destinationWorkspace =
        new Workspace(
            DESTINATION_WORKSPACE_ID,
            DESTINATION_WORKSPACE_USERFACING_ID,
            /*displayName=*/ null,
            /*description=*/ null,
            new SpendProfileId(null),
            Collections.emptyMap(),
            WorkspaceStage.MC_WORKSPACE);
    inputParameters.put(ControlledResourceKeys.SOURCE_WORKSPACE_ID, SOURCE_WORKSPACE_ID);
    inputParameters.put(JobMapKeys.REQUEST.getKeyName(), destinationWorkspace);

    when(mockFlightContext.getInputParameters()).thenReturn(inputParameters);
    when(mockFlightContext.getWorkingMap()).thenReturn(workingMap);

    StepResult stepResult = cloneAllFoldersStep.doStep(mockFlightContext);
    assertEquals(StepResult.getStepResultSuccess(), stepResult);

    Map<String, String> folderIdMap =
        workingMap.get(FolderKeys.FOLDER_IDS_TO_CLONE_MAP, new TypeReference<>() {});
    assertNotNull(folderIdMap.get(SOURCE_PARENT_FOLDER_ID.toString()));
    assertNotNull(folderIdMap.get(SOURCE_SON_FOLDER_ID.toString()));

    verify(mockFolderDao, times(1))
        .createFolder(
            eq(
                new Folder(
                    UUID.fromString(folderIdMap.get(SOURCE_PARENT_FOLDER_ID.toString())),
                    DESTINATION_WORKSPACE_ID,
                    SOURCE_PARENT_FOLDER_NAME,
                    SOURCE_PARENT_FOLDER_DESCRIPTION,
                    /*parentFolderId=*/ null,
                    /*properties=*/ null)));
    verify(mockFolderDao, times(1))
        .createFolder(
            eq(
                new Folder(
                    UUID.fromString(folderIdMap.get(SOURCE_SON_FOLDER_ID.toString())),
                    DESTINATION_WORKSPACE_ID,
                    SOURCE_SON_FOLDER_NAME,
                    SOURCE_SON_FOLDER_DESCRIPTION,
                    /*parentFolderId=*/ null,
                    /*properties=*/ null)));

    verify(mockFolderDao, times(1))
        .updateFolder(
            eq(DESTINATION_WORKSPACE_ID),
            eq(UUID.fromString(folderIdMap.get(SOURCE_SON_FOLDER_ID.toString()))),
            /*displayName=*/ eq(null),
            /*description=*/ eq(null),
            eq(UUID.fromString(folderIdMap.get(SOURCE_PARENT_FOLDER_ID.toString()))),
            /*updateParent=*/ eq(true));
  }
}
