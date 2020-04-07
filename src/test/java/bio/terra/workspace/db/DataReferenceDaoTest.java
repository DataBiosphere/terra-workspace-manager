package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.model.DataReference;
import bio.terra.workspace.model.DataRepoSnapshot;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class DataReferenceDaoTest {

  @Autowired WorkspaceManagerJdbcConfiguration jdbcConfiguration;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired DataReferenceDao dataReferenceDao;
  @Autowired WorkspaceDao workspaceDao;

  private UUID workspaceId;
  private UUID spendProfileId;
  private UUID referenceId;

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    referenceId = UUID.randomUUID();
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void verifyCreatedDataReferenceExists() {
    workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());

    dataReferenceDao.createDataReference(
        referenceId, workspaceId, "testName", "tdr-snapshot", new DataRepoSnapshot("foo", "bar"));
    DataReference reference = dataReferenceDao.getDataReference(referenceId);

    assertThat(reference.getWorkspaceId(), equalTo(workspaceId));
    assertThat(reference.getReferenceId(), equalTo(referenceId));
    assertThat(reference.getName(), equalTo("testName"));
    assertThat(reference.getReferenceType(), equalTo("tdr-snapshot"));
    assertThat(reference.getReference().getSnapshotId(), equalTo("foo"));
    assertThat(reference.getReference().getInstance(), equalTo("bar"));
  }

  public void verifyCreateDataReferenceRequiresWorkspace() {}

  public void verifyGetDataReference() {}

  public void verifyDeleteDataReference() {}
}
