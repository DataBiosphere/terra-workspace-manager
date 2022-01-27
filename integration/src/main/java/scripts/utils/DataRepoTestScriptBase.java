package scripts.utils;

import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class DataRepoTestScriptBase extends WorkspaceAllocateTestScriptBase {
  private static final String DATA_REFERENCE_NAME_PREFIX = "REF_";

  private String dataRepoSnapshotId;
  private String dataRepoInstanceName;

  /**
   * Allow inheriting classes to obtain the Data Repo snapshot ID
   *
   * @return Data Repo Snapshot ID string
   */
  protected String getDataRepoSnapshotId() {
    return dataRepoSnapshotId;
  }

  /**
   * Allow inheriting classes to obtain the Data Repo Instance Name
   *
   * @return Data Repo Instance Name string
   */
  protected String getDataRepoInstanceName() {
    return dataRepoInstanceName;
  }

  @Override
  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    // Spend Profile ID is already read from the super class
    dataRepoSnapshotId = ParameterUtils.getDataRepoSnapshot(parameters);
    dataRepoInstanceName = ParameterUtils.getDataRepoInstance(parameters);
  }

  /**
   * Generate and return a CreateDataReferenceRequestBody. The returned object consists of a unique
   * name for the data reference, standard cloning instructions, standard reference type, and a
   * reference using the Data Repo Snapshot ID and Instance Name arguments defined in the test
   * config.
   *
   * @return Request body for creating a data reference
   */
  protected CreateDataReferenceRequestBody getTestCreateDataReferenceRequestBody() {
    return new CreateDataReferenceRequestBody()
        .name(getUniqueDataReferenceName())
        .cloningInstructions(CloningInstructionsEnum.REFERENCE)
        .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
        .reference(getTestDataRepoSnapshot());
  }

  /**
   * Enumerate data references
   *
   * @return A list of data references
   */
  protected List<DataReferenceDescription> getDataReferenceDescriptions(
      UUID workspaceId, WorkspaceApi workspaceApi, int offset, int limit) throws ApiException {
    final DataReferenceList dataReferenceListFirstPage =
        workspaceApi.enumerateReferences(workspaceId, offset, limit);
    return dataReferenceListFirstPage.getResources();
  }

  private String getUniqueDataReferenceName() {
    String name = DATA_REFERENCE_NAME_PREFIX + UUID.randomUUID().toString();
    // Reformat the UUID (replace hyphens with underscores) in order to meet the rules for data
    // reference names
    return name.replace("-", "_");
  }

  private DataRepoSnapshot getTestDataRepoSnapshot() {
    return new DataRepoSnapshot()
        .snapshot(getDataRepoSnapshotId())
        .instanceName(getDataRepoInstanceName());
  }
}
