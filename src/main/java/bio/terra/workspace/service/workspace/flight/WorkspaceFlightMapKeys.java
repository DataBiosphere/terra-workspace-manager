package bio.terra.workspace.service.workspace.flight;

public final class WorkspaceFlightMapKeys {
  public static final String WORKSPACE_ID = "workspaceId";
  public static final String SPEND_PROFILE_ID = "spendProfileId";
  public static final String GOOGLE_PROJECT_ID = "googleProjectId";
  public static final String WORKSPACE_STAGE = "workspaceStage";
  public static final String BILLING_ACCOUNT_ID = "billingAccountId";
  public static final String IAM_OWNER_GROUP_EMAIL = "iamOwnerGroupEmail";
  public static final String IAM_WRITER_GROUP_EMAIL = "iamWriterGroupEmail";
  public static final String IAM_READER_GROUP_EMAIL = "iamReaderGroupEmail";
  public static final String RBS_RESOURCE_ID = "rbsResourceId";
  public static final String CLOUD_CONTEXT_ID = "cloudContextId";

  private WorkspaceFlightMapKeys() {}

  /** Use inner class for new set of keys so it's easy to spot duplicates */
  public static class ControlledResourceKeys {
    public static final String RESOURCE_ID = "controlledResourceId";
    public static final String OWNER_EMAIL = "controlledResourceOwnerEmail";

    private ControlledResourceKeys() {}
  }
}
