package bio.terra.workspace.service.resource.controlled.cloud.gcp;

/** Constants shared among resource types. */
public class GcpResourceConstants {

  /** Default region of a resource. */
  public static final String DEFAULT_REGION = "us-central1";

  /** Default zone of the resource. */
  public static final String DEFAULT_ZONE = "us-central1-a";

  /**
   * The workspaceId metadata key for gcp compute resources used by the cli to set the current
   * workspace during post startup
   */
  public static final String WORKSPACE_ID_METADATA_KEY = "terra-workspace-id";

  /**
   * The CLI server environment metadata key for gcp compute resources used to point the terra CLI
   * at the correct WSM and SAM instances.
   */
  public static final String SERVER_ID_METADATA_KEY = "terra-cli-server";

  /** The resourceId metadata key for gcp compute resources. */
  public static final String RESOURCE_ID_METADATA_KEY = "terra-resource-id";

  /** The app proxy metadata key. */
  public static final String PROXY_METADATA_KEY = "terra-app-proxy";

  /**
   * The main git branch for the workspace manager repo used to point used for sourcing the latest
   * startup script
   */
  public static final String MAIN_BRANCH = "main";
  /** The GCE instance enable guest attributes metadata key */
  public static final String ENABLE_GUEST_ATTRIBUTES_METADATA_KEY = "enable-guest-attributes";
  /**
   * The GCE instance startup script url metadata key for providing a startup script to the instance
   */
  public static final String STARTUP_SCRIPT_URL_METADATA_KEY = "startup-script-url";
}
