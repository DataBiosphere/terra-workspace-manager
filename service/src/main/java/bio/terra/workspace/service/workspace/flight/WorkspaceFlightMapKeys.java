package bio.terra.workspace.service.workspace.flight;

public final class WorkspaceFlightMapKeys {
  public static final String WORKSPACE_ID = "workspaceId";
  public static final String SPEND_PROFILE_ID = "spendProfileId";
  public static final String GCP_PROJECT_ID = "gcpProjectId";
  public static final String WORKSPACE_STAGE = "workspaceStage";
  public static final String BILLING_ACCOUNT_ID = "billingAccountId";
  public static final String IAM_GROUP_EMAIL_MAP = "iamGroupEmailMap";
  public static final String RBS_RESOURCE_ID = "rbsResourceId";
  public static final String DISPLAY_NAME_ID = "displayNameId";
  public static final String DESCRIPTION_ID = "descriptionId";

  private WorkspaceFlightMapKeys() {}

  /** Use inner class for new set of keys so it's easy to spot duplicates */
  public static class ControlledResourceKeys {

    private ControlledResourceKeys() {}

    public static final String CREATION_PARAMETERS = "creationParameters";
    public static final String PRIVATE_RESOURCE_IAM_ROLES = "iamRoles";
    public static final String IAM_RESOURCE_GROUP_EMAIL_MAP = "iamResourceGroupEmailMap";

    public static final String UPDATE_PARAMETERS = "updateParameters";
    public static final String PREVIOUS_UPDATE_PARAMETERS = "previousUpdateParameters";

    public static final String RESOURCE_NAME = "resourceName";
    public static final String PREVIOUS_RESOURCE_NAME = "previousResourceName";
    public static final String RESOURCE_DESCRIPTION = "resourceDescription";
    public static final String PREVIOUS_RESOURCE_DESCRIPTION = "previousResourceDescription";

    // Notebooks keys
    public static final String CREATE_NOTEBOOK_NETWORK_NAME = "createNotebookNetworkName";
    public static final String CREATE_NOTEBOOK_PARAMETERS = "createNotebookParameters";
    public static final String CREATE_NOTEBOOK_REGION = "createNotebookRegion";
    public static final String CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID =
        "createNotebookServiceAccountId";
    public static final String CREATE_NOTEBOOK_SUBNETWORK_NAME = "createNotebookSubnetworkName";
    public static final String DELETE_NOTEBOOK_SERVICE_ACCOUNT_EMAIL =
        "deleteNotebookServiceAccountEmail";

    // Cloning Keys
    public static final String CLONED_RESOURCE_DEFINITION = "clonedResourceDefinition";
    public static final String DESTINATION_WORKSPACE_ID = "destinationWorkspaceId";
    public static final String DESTINATION_BUCKET_NAME = "destinationBucketName";
    public static final String LOCATION = "location";
    public static final String CLONING_INSTRUCTIONS = "cloningInstructions";
  }

  /** Common resource keys */
  public static class ResourceKeys {
    public static final String RESOURCE_ID = "resourceId";
    public static final String RESOURCE_TYPE = "resourceType";

    private ResourceKeys() {}
  }
}
