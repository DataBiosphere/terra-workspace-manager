package bio.terra.workspace.service.workspace.flight;

public final class WorkspaceFlightMapKeys {
  public static final String WORKSPACE_ID = "workspaceId";
  public static final String SPEND_PROFILE_ID = "spendProfileId";
  public static final String GCP_PROJECT_ID = "gcpProjectId";
  public static final String WORKSPACE_ID_TO_GCP_PROJECT_ID_MAP = "workspaceIdToGcpProjectIdMap";
  public static final String WORKSPACE_STAGE = "workspaceStage";
  public static final String IAM_GROUP_EMAIL_MAP = "iamGroupEmailMap";
  public static final String RBS_RESOURCE_ID = "rbsResourceId";
  public static final String USER_TO_REMOVE = "userToRemove";
  public static final String ROLE_TO_REMOVE = "roleToRemove";
  public static final String APPLICATION_IDS = "applicationIds";
  public static final String OPERATION_TYPE = "operationType";
  public static final String POLICIES = "policies";
  public static final String EFFECTIVE_POLICIES = "effectivePolicies";
  public static final String FOLDER_ID = "folderId";
  public static final String MERGE_POLICIES = "mergePolicies";
  public static final String IS_WET_RUN = "isWetRun";
  public static final String UPDATED_WORKSPACES = "updatedWorkspaces";
  public static final String SPEND_PROFILE = "spendProfile";
  public static final String PET_SA_CREDENTIALS = "petSaCredentials";

  private WorkspaceFlightMapKeys() {}

  /** Use inner class for new set of keys so it's easy to spot duplicates */
  public static class ControlledResourceKeys {

    private ControlledResourceKeys() {}

    public static final String CREATION_PARAMETERS = "creationParameters";
    public static final String PRIVATE_RESOURCE_IAM_ROLE = "privateResourceIamRole";
    public static final String GCP_CLOUD_CONTEXT = "gcpCloudContext";
    public static final String AZURE_CLOUD_CONTEXT = "azureCloudContext";
    public static final String PREVIOUS_UPDATE_PARAMETERS = "previousUpdateParameters";
    public static final String CREATE_RESOURCE_REGION = "createResourceRegion";

    public static final String RESOURCE_ROLES_TO_REMOVE = "resourceRolesToRemove";
    public static final String REMOVED_USER_IS_WORKSPACE_MEMBER = "removedUserIsWorkspaceMember";

    public static final String CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME =
        "controlledBigQueryDatasetsWithoutLifetime";
    public static final String CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP =
        "controlledBigQueryDatasetToTableLifetimeMap";
    public static final String CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP =
        "controlledBigQueryDatasetToPartitionLifetimeMap";
    public static final String CONTROLLED_RESOURCE_ID_TO_REGION_MAP =
        "controlledResourceToRegionMap";
    public static final String CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP =
        "controlledResourceIdToWorkspaceIdMap";
    // Notebooks keys
    public static final String CREATE_NOTEBOOK_NETWORK_NAME = "createNotebookNetworkName";
    public static final String CREATE_NOTEBOOK_PARAMETERS = "createNotebookParameters";
    public static final String CREATE_NOTEBOOK_LOCATION = "createNotebookLocation";
    public static final String CREATE_NOTEBOOK_SUBNETWORK_NAME = "createNotebookSubnetworkName";
    public static final String NOTEBOOK_PET_SERVICE_ACCOUNT = "notebookPetServiceAccount";

    // Cloning Keys
    public static final String CLONE_ALL_RESOURCES_FLIGHT_ID = "cloneAllResourcesFlightId";
    public static final String CLONE_DEFINITION_RESULT = "cloneDefinitionResult";
    public static final String CLONED_RESOURCE_DEFINITION = "clonedResourceDefinition";
    public static final String CONTROL_PLANE_PROJECT_ID = "controlPlaneProjectId";
    public static final String CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID = "createGcpCloudContextFlightId";
    public static final String CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID =
        "createAzureCloudContextFlightId";
    public static final String DESTINATION_BUCKET_NAME = "destinationBucketName";
    public static final String DESTINATION_CLONE_INPUTS = "destinationCloneInputs";
    public static final String DESTINATION_DATASET_NAME = "destinationDatasetName";
    public static final String DESTINATION_REFERENCED_RESOURCE = "destinationReferencedResource";
    public static final String DESTINATION_WORKSPACE_ID = "destinationWorkspaceId";
    public static final String LOCATION = "location";
    public static final String RESOURCE_ID_TO_CLONE_RESULT = "resourceIdToCloneResult";
    public static final String RESOURCES_TO_CLONE = "resourcesToClone";
    public static final String CONTROLLED_RESOURCES_TO_DELETE = "controlledResourcesToDelete";
    public static final String SOURCE_CLONE_INPUTS = "sourceCloneInputs";
    public static final String SOURCE_WORKSPACE_ID = "sourceWorkspaceId";
    public static final String STORAGE_TRANSFER_JOB_NAME = "storageTransferJobName";
    public static final String STORAGE_TRANSFER_SERVICE_SA_EMAIL = "storageTransferServiceSAEmail";
    public static final String TABLE_TO_JOB_ID_MAP = "tableToJobIdMap";
    public static final String WORKSPACE_CREATE_FLIGHT_ID = "workspaceCreateFlightId";
    public static final String SHARED_STORAGE_ACCOUNT = "sharedStorageAccount";
    public static final String STORAGE_ACCOUNT_NAME = "storageAccountName";
    public static final String BATCH_ACCOUNT_NAME = "batchAccountName";
    public static final String DESTINATION_RESOURCE_ID = "destinationResourceId";
    public static final String DESTINATION_FOLDER_ID = "destinationFolderId";
    public static final String DESTINATION_CONTAINER_NAME = "destinationContainerName";
  }

  public static class FolderKeys {

    private FolderKeys() {}

    // Mapping the source workspace folder id to the new created destination workspace folder id
    public static final String FOLDER_IDS_TO_CLONE_MAP = "folderIdsToCloneMap";
  }

  public static class ReferencedResourceKeys {

    private ReferencedResourceKeys() {}

    public static final String REFERENCED_RESOURCES_TO_DELETE = "referencedResourcesToDelete";
  }

  /** Common resource keys */
  public static class ResourceKeys {
    public static final String RESOURCE_ID = "resourceId";
    public static final String RESOURCE_TYPE = "resourceType";
    public static final String RESOURCE_NAME = "resourceName";
    public static final String PREVIOUS_RESOURCE_NAME = "previousResourceName";
    public static final String RESOURCE_DESCRIPTION = "resourceDescription";
    public static final String PREVIOUS_RESOURCE_DESCRIPTION = "previousResourceDescription";
    public static final String PREVIOUS_ATTRIBUTES = "previousAttributes";
    public static final String PREVIOUS_CLONING_INSTRUCTIONS = "previousCloningInstructions";
    public static final String STEWARDSHIP_TYPE = "stewardshipType";
    public static final String RESOURCE = "resource";
    public static final String DESTINATION_RESOURCE = "destinationResource";
    public static final String CLONING_INSTRUCTIONS = "cloningInstructions";
    public static final String RESOURCE_STATE_RULE = "resourceStateRule";
    public static final String RESOURCE_STATE_CHANGED = "resourceStateChanged";
    public static final String UPDATE_PARAMETERS = "updateParameters";
    public static final String COMMON_UPDATE_PARAMETERS = "commonUpdateParameters";
    public static final String DB_UPDATER = "dbUpdater";
    public static final String UPDATE_COMPLETE = "updateComplete";

    private ResourceKeys() {}
  }

  /** Pet SA keys */
  public static class PetSaKeys {
    public static final String MODIFIED_PET_SA_POLICY_ETAG = "modifiedPetSaPolicyEtag";

    private PetSaKeys() {}
  }

  /** Workspace application keys */
  public static class WsmApplicationKeys {
    public static final String WSM_APPLICATION = "wsmApplication";
    public static final String APPLICATION_ABLE_DAO = "applicationAbleDao";
    public static final String APPLICATION_ABLE_SAM = "applicationAbleSam";
    public static final String APPLICATION_ABLE_ENUM = "applicationAbleEnum";

    private WsmApplicationKeys() {}
  }
}
