package bio.terra.workspace.service.iam.model;

public class SamConstants {

  public static class SamResource {
    public static final String WORKSPACE = "workspace";
    public static final String CONTROLLED_USER_SHARED = "controlled-user-shared-workspace-resource";
    public static final String CONTROLLED_USER_PRIVATE =
        "controlled-user-private-workspace-resource";
    public static final String CONTROLLED_APPLICATION_SHARED =
        "controlled-application-shared-workspace-resource";
    public static final String CONTROLLED_APPLICATION_PRIVATE =
        "controlled-application-private-workspace-resource";
    public static final String SPEND_PROFILE = "spend-profile";
    public static final String AZURE_MANAGED_IDENTITY = "azure_managed_identity";

    private SamResource() {}
  }

  public static class SamWorkspaceAction {
    public static final String DISCOVER = "discover";
    public static final String READ = "read";
    public static final String WRITE = "write";
    public static final String OWN = "own";
    public static final String DELETE = "delete";
    public static final String READ_JOB_RESULT = "read_job_result";
    public static final String READ_IAM = "read_policies";
    public static final String CREATE_CONTROLLED_USER_SHARED = "create_controlled_user_shared";
    public static final String CREATE_CONTROLLED_USER_PRIVATE = "create_controlled_user_private";
    public static final String CREATE_CONTROLLED_APPLICATION_SHARED =
        "create_controlled_application_shared";
    public static final String CREATE_CONTROLLED_APPLICATION_PRIVATE =
        "create_controlled_application_private";
    public static final String CREATE_REFERENCE = "create_referenced_resource";
    public static final String UPDATE_REFERENCE = "update_referenced_resource";
    public static final String DELETE_REFERENCE = "delete_referenced_resource";

    private SamWorkspaceAction() {}
  }

  public static class SamControlledResourceActions {
    public static final String READ_ACTION = "read";
    public static final String WRITE_ACTION = "write";
    public static final String EDIT_ACTION = "edit";
    public static final String DELETE_ACTION = "delete";

    private SamControlledResourceActions() {}
  }

  public static class SamSpendProfileAction {
    public static final String LINK = "link";

    private SamSpendProfileAction() {}
  }

  public static class SamAzureManagedIdentityAction {
    public static final String IDENTIFY = "identify";

    private SamAzureManagedIdentityAction() {}
  }
}
