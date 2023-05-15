package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
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
    UUID workspaceId = UUID.randomUUID();
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceId)
            .userFacingId("a" + workspaceId)
            .description("A")
            .spendProfileId(WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
            .build();

    workspaceDao.createWorkspace(workspace, null);

    String flightId = workspaceId.toString();
    workspaceDao.createCloudContextStart(
        workspaceId, CloudPlatform.GCP, WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID, flightId);

    GcpCloudContext cloudContext =
        new GcpCloudContext(
            "gcpProjectId",
            "samPolicyOwner",
            "samPolicyWriter",
            "samPolicyReader",
            "samPolicyApplication",
            null);

    workspaceDao.createCloudContextSuccess(
        workspaceId, CloudPlatform.GCP, cloudContext.serialize(), flightId);

    jdbcTemplate.update(
        "UPDATE cloud_context SET spend_profile = NULL", new MapSqlParameterSource());

    Optional<DbCloudContext> dbCloudContextOptional =
        workspaceDao.getCloudContext(workspaceId, CloudPlatform.GCP);
    DbCloudContext dbCloudContext = dbCloudContextOptional.orElseThrow();
    Assertions.assertNull(dbCloudContext.getSpendProfile());

    workspaceDao.backfillCloudContextSpendProfile();

    dbCloudContextOptional = workspaceDao.getCloudContext(workspaceId, CloudPlatform.GCP);
    dbCloudContext = dbCloudContextOptional.orElseThrow();
    Assertions.assertNotNull(dbCloudContext.getSpendProfile());
    Assertions.assertEquals(
        dbCloudContext.getSpendProfile().getId(),
        WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID.getId());
  }
}