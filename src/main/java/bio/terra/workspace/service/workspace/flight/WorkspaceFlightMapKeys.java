package bio.terra.workspace.service.workspace.flight;

public final class WorkspaceFlightMapKeys {
  public static final String WORKSPACE_ID = "workspaceId";
  public static final String SPEND_PROFILE_ID = "spendProfileId";
  public static final String GCP_PROJECT_ID = "gcpProjectId";
  public static final String WORKSPACE_STAGE = "workspaceStage";
  public static final String BILLING_ACCOUNT_ID = "billingAccountId";
  public static final String IAM_OWNER_GROUP_EMAIL = "iamOwnerGroupEmail";
  public static final String IAM_APPLICATION_GROUP_EMAIL = "iamApplicationGroupEmail";
  public static final String IAM_WRITER_GROUP_EMAIL = "iamWriterGroupEmail";
  public static final String IAM_READER_GROUP_EMAIL = "iamReaderGroupEmail";
  public static final String RBS_RESOURCE_ID = "rbsResourceId";
  public static final String DISPLAY_NAME_ID = "displayNameId";
  public static final String DESCRIPTION_ID = "descriptionId";

  private WorkspaceFlightMapKeys() {}

  /** Use inner class for new set of keys so it's easy to spot duplicates */
  public static class ControlledResourceKeys {
    public static final String CREATION_PARAMETERS = "creationParameters";
    public static final String PRIVATE_RESOURCE_IAM_ROLES = "iamRoles";

    private ControlledResourceKeys() {}
  }

  /** Common resource keys */
  public static class ResourceKeys {
    public static final String RESOURCE_ID = "resourceId";
    public static final String RESOURCE_TYPE = "resourceType";

    private ResourceKeys() {}
  }
}
