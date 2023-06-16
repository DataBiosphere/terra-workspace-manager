package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class CloudContextSpendProfileBackfillTest extends BaseUnitTest {
  @Autowired WorkspaceDao workspaceDao;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  public void testBackfillQuery() {
    UUID workspaceId = WorkspaceUnitTestUtils.createWorkspaceWithGcpContext(workspaceDao);

    // Set spend profile to null and verity
    jdbcTemplate.update(
        "UPDATE cloud_context SET spend_profile = NULL", new MapSqlParameterSource());
    Optional<DbCloudContext> dbCloudContextOptional =
        workspaceDao.getCloudContext(workspaceId, CloudPlatform.GCP);
    DbCloudContext dbCloudContext = dbCloudContextOptional.orElseThrow();
    Assertions.assertNull(dbCloudContext.getSpendProfile());

    // Run the backfill
    workspaceDao.backfillCloudContextSpendProfile();

    // Verify that it backfilled
    dbCloudContextOptional = workspaceDao.getCloudContext(workspaceId, CloudPlatform.GCP);
    dbCloudContext = dbCloudContextOptional.orElseThrow();
    Assertions.assertNotNull(dbCloudContext.getSpendProfile());
    Assertions.assertEquals(
        dbCloudContext.getSpendProfile().getId(), DEFAULT_SPEND_PROFILE_ID.getId());
  }
}
