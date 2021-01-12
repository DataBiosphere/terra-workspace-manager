package scripts.utils;

import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.model.ReferenceTypeEnum;
import java.util.UUID;

public class DataReferenceUtils {

  public static CreateDataReferenceRequestBody defaultDataReferenceRequest() {
    // This method relies on a persistent snapshot existing in Data Repo, currently in dev.
    // This snapshot was created using our dev service account, which is a steward in dev Data Repo.
    // First, I created a dataset "wm_integration_test_dataset" using TDR's
    // snapshot-test-dataset.json. Then, I created the snapshot
    // "workspace_integration_test_snapshot" using the "byFullView" mode. Finally, I added the
    // integration test user as a reader of this snapshot.
    // These steps should only need to be repeated if the dev DataRepo data is deleted, or to
    // support this test in other DataRepo environments.
    // Data Repo makes a reasonable effort to maintain their dev environment, so this should be a
    // very rare occurrence.
    DataRepoSnapshot snapshotReference =
        new DataRepoSnapshot()
            .snapshot("97b5559a-2f8f-4df3-89ae-5a249173ee0c")
            .instanceName("terra");
    // Names need to be unique per reference type within a workspace, use a random name here
    // to support multiple test threads running at once.
    String dataReferenceName = "snapshot_" + UUID.randomUUID().toString().replace("-", "_");
    CreateDataReferenceRequestBody referenceRequest =
        new CreateDataReferenceRequestBody()
            .name(dataReferenceName)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(snapshotReference)
            .cloningInstructions(CloningInstructionsEnum.NOTHING);
    return referenceRequest;
  }
}
