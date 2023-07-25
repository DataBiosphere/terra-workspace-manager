package bio.terra.workspace.common.mocks;

import org.springframework.stereotype.Component;

@Component
public class MockWorkspaceV1Api {

  // Workspace

  public static final String WORKSPACES_V1_CREATE = "/api/workspaces/v1";
  public static final String WORKSPACES_V1 = WORKSPACES_V1_CREATE + "/%s";
  public static final String WORKSPACES_V1_BY_UFID =
      WORKSPACES_V1_CREATE + "/workspaceByUserFacingId/%s";
  public static final String WORKSPACES_V1_CLONE = WORKSPACES_V1 + "/clone";
  public static final String WORKSPACES_V1_CLONE_RESULT = WORKSPACES_V1 + "/clone-result/%s";

  // Cloud context

  public static final String CLOUD_CONTEXTS_V1 = WORKSPACES_V1 + "/cloudcontexts";
  public static final String CLOUD_CONTEXT_V2_CREATE_RESULT = CLOUD_CONTEXTS_V1 + "/result/%s";

  // Properties

  public static final String WORKSPACES_V1_PROPERTIES = WORKSPACES_V1 + "/properties";

  // Policies & region

  public static final String WORKSPACES_V1_POLICIES = WORKSPACES_V1 + "/policies";
  public static final String WORKSPACES_V1_POLICIES_EXPLAIN = WORKSPACES_V1_POLICIES + "/explain";
  public static final String WORKSPACES_V1_POLICIES_MERGE_CHECK =
      WORKSPACES_V1_POLICIES + "/mergeCheck";
  public static final String WORKSPACES_V1_LIST_VALID_REGIONS = WORKSPACES_V1 + "/listValidRegions";

  // Roles

  public static final String WORKSPACES_V1_GRANT_ROLE = WORKSPACES_V1 + "/roles/%s/members";
  public static final String WORKSPACES_V1_REMOVE_ROLE = WORKSPACES_V1_GRANT_ROLE + "/%s";

  // Resources

  public static final String WORKSPACES_V1_RESOURCES = WORKSPACES_V1 + "/resources";
  public static final String WORKSPACES_V1_RESOURCES_PROPERTIES =
      WORKSPACES_V1_RESOURCES + "/%s/properties";
}
