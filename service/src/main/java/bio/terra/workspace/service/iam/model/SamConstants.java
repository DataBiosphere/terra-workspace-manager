package bio.terra.workspace.service.iam.model;

public class SamConstants {

  public static final String SAM_WORKSPACE_RESOURCE = "workspace";
  public static final String SAM_WORKSPACE_READ_ACTION = "read";
  public static final String SAM_WORKSPACE_WRITE_ACTION = "write";
  public static final String SAM_WORKSPACE_OWN_ACTION = "own";
  public static final String SAM_WORKSPACE_DELETE_ACTION = "delete";
  public static final String SAM_WORKSPACE_READ_IAM_ACTION = "read_policies";
  public static final String SAM_CREATE_REFERENCED_RESOURCE = "create_referenced_resource";
  public static final String SAM_UPDATE_REFERENCED_RESOURCE = "update_referenced_resource";
  public static final String SAM_DELETE_REFERENCED_RESOURCE = "delete_referenced_resource";
  public static final String SPEND_PROFILE_RESOURCE = "spend-profile";
  public static final String SPEND_PROFILE_LINK_ACTION = "link";

  public static class SamControlledResourceActions {
    public static final String READ_ACTION = "read";
    public static final String EDIT_ACTION = "edit";
    public static final String DELETE_ACTION = "delete";

    private SamControlledResourceActions() {}
  }
}
