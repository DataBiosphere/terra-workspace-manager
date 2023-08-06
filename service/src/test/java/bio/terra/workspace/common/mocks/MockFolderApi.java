package bio.terra.workspace.common.mocks;

public class MockFolderApi {

  public static final String CREATE_FOLDERS_PATH_FORMAT = "/api/workspaces/v1/%s/folders";
  public static final String FOLDERS_PATH_FORMAT = CREATE_FOLDERS_PATH_FORMAT + "/%s";
  public static final String FOLDERS_PROPERTIES_PATH_FORMAT = FOLDERS_PATH_FORMAT + "/properties";
  public static final String DELETE_RESULT_FOLDERS_PATH_FORMAT = FOLDERS_PATH_FORMAT + "/result/%s";
}
