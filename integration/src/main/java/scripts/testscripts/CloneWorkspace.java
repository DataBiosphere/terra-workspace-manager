package scripts.testscripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import java.util.List;
import scripts.utils.DataRepoTestScriptBase;

public class CloneWorkspace extends DataRepoTestScriptBase {

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Populate source resource
    // Create a GCS bucket with data
    // create a private GCS bucket
    // create a GCS bucket with data and COPY_NOTHING instruction
    // create a GCS bucket with data and COPY_DEFINITION
    // Create a BigQuery Dataset with tables and COPY_RESOURCE
    // Create a BigQuery dataset with tables and COPY_DEFINITION
    // Create a private BQ dataset
    // Create reference to GCS bucket with COPY_REFERENCE
    // create reference to BQ dataset with COPY_NOTHING
    // create reference to Data Repo Snapshot
    // Give the second user read access to the workspace
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // As reader user, clone the workspace
    // Verify first GCS bucket clone has data
    // Verify clone of private bucket fails
    // Verify COPY_NOTHING bucket was skipped
    // verify COPY_DEFINITION bucket exists but is empty
    // verify COPY_RESOURCE bucket exists and has data
    // verify COPY_DEFINITION dataset exists but has no tables
    // verify private dataset clone failed

  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    // Delete the cloned workspace (will delete contents)
  }
}
