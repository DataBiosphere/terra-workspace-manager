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
}
