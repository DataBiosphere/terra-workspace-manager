package bio.terra.workspace.common.utils;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A collection of utilities and constants useful for MockMVC-based tests. This style of tests lets
 * us test controller-layer code (request/response parsing, authz, and validation) without actually
 * spinning up a local server.
 *
 * <p>TODO: it's probably worth looking into whether we can automatically pull routes from the
 * generated swagger, instead of manually wrapping them here.
 */
public class MockMvcUtils {

  public static final String AUTH_HEADER = "Authorization";

  public static final String WORKSPACES_V1_PATH = "/api/workspaces/v1";
  public static final String WORKSPACES_V1_BY_UUID_PATH_FORMAT = "/api/workspaces/v1/%s";
  public static final String WORKSPACES_V1_BY_UFID_PATH_FORMAT =
      "/api/workspaces/v1/workspaceByUserFacingId/%s";
  public static final String ADD_USER_TO_WORKSPACE_PATH_FORMAT =
      "/api/workspaces/v1/%s/roles/%s/members";
  public static final String CLONE_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1/%s/clone";
  public static final String UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/properties";
  public static final String CREATE_SNAPSHOT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String CREATE_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts";
  public static final String GET_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts/result/%s";

  public static final String CREATE_AZURE_IP_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/ip";
  public static final String CREATE_AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks";
  public static final String CREATE_AZURE_NETWORK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/network";
  public static final String CREATE_AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm";

  public static final String GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/generateName";
  public static final String GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/generateName";
  public static final String GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/generateName";

  public static MockHttpServletRequestBuilder addAuth(
      MockHttpServletRequestBuilder request, AuthenticatedUserRequest userRequest) {
    return request.header(AUTH_HEADER, "Bearer " + userRequest.getRequiredToken());
  }

  public static MockHttpServletRequestBuilder addJsonContentType(
      MockHttpServletRequestBuilder request) {
    return request.contentType("application/json");
  }
}
