package bio.terra.workspace.db;

import static bio.terra.workspace.db.WorkspaceActivityLogDao.ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** DAO in the test to contain direct access to the database */
@Component
public class RawDaoTestFixture {
  private static final Logger logger = LoggerFactory.getLogger(RawDaoTestFixture.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public RawDaoTestFixture(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public void storeControlledGcpResource(
      UUID workspaceUuid, UUID resourceId, String resourceAttributes) {
    storeResource(
        workspaceUuid.toString(),
        CloudPlatform.GCP.name(),
        resourceId.toString(),
        RandomStringUtils.randomAlphabetic(10),
        RandomStringUtils.randomAlphabetic(100),
        StewardshipType.CONTROLLED.toSql(),
        WsmResourceType.CONTROLLED_GCP_GCS_BUCKET.toSql(),
        WsmResourceFamily.GCS_BUCKET.toSql(),
        CloningInstructions.COPY_NOTHING.toSql(),
        resourceAttributes,
        AccessScopeType.ACCESS_SCOPE_SHARED.toSql(),
        ManagedByType.MANAGED_BY_APPLICATION.toSql(),
        null,
        null,
        PrivateResourceState.NOT_APPLICABLE.toSql());
  }

  // Write a resource into the database from bare parts
  @WriteTransaction
  public void storeResource(
      String workspaceUuid,
      String cloudPlatform,
      String resourceId,
      String name,
      String description,
      String stewardshipType,
      String resourceType,
      String resourceFamily,
      String cloningInstructions,
      String attributes,
      String accessScope,
      String managedBy,
      String associatedApp,
      String assignedUser,
      String privateResourceState) {
    String sql =
        "INSERT INTO resource (workspace_id, cloud_platform, resource_id, name, description, stewardship_type,"
            + " exact_resource_type, resource_type, cloning_instructions, attributes,"
            + " access_scope, managed_by, associated_app, assigned_user, private_resource_state)"
            + " VALUES (:workspace_id, :cloud_platform, :resource_id, :name, :description, :stewardship_type,"
            + " :exact_resource_type, :resource_type, :cloning_instructions, cast(:attributes AS jsonb),"
            + " :access_scope, :managed_by, :associated_app, :assigned_user, :private_resource_state)";

    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid)
            .addValue("cloud_platform", cloudPlatform)
            .addValue("resource_id", resourceId)
            .addValue("name", name)
            .addValue("description", description)
            .addValue("stewardship_type", stewardshipType)
            .addValue("exact_resource_type", resourceType)
            .addValue("resource_type", resourceFamily)
            .addValue("cloning_instructions", cloningInstructions)
            .addValue("attributes", attributes)
            .addValue("access_scope", accessScope)
            .addValue("managed_by", managedBy)
            .addValue("associated_app", associatedApp)
            .addValue("assigned_user", assignedUser)
            .addValue("private_resource_state", privateResourceState);

    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for resource {} for workspace {}", resourceId, workspaceUuid);
    } catch (DuplicateKeyException e) {
      throw new DuplicateResourceException(
          String.format(
              "A resource already exists in the workspace that has the same name (%s) or the same id (%s)",
              name, resourceId));
    }
  }

  @WriteTransaction
  public void writeActivityLogWithTimestamp(
      UUID workspaceId,
      String actorEmail,
      OffsetDateTime timestamp,
      String changeSubjectId,
      ActivityLogChangedTarget changedTarget) {
    String sql =
        """
            INSERT INTO workspace_activity_log (workspace_id, change_date, change_type, actor_email, actor_subject_id,
            change_subject_id, change_subject_type)
            VALUES (:workspace_id, :change_date, :change_type, :actor_email, :actor_subject_id, :change_subject_id, :change_subject_type)
        """;
    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_date", timestamp)
            .addValue("change_type", OperationType.CREATE.name())
            .addValue("actor_email", actorEmail)
            .addValue("actor_subject_id", RandomStringUtils.randomAlphabetic(5))
            .addValue("change_subject_id", changeSubjectId)
            .addValue("change_subject_type", changedTarget.name());
    jdbcTemplate.update(sql, params);
  }

  @ReadTransaction
  public List<ActivityLogChangeDetails> readActivityLogs(
      UUID workspaceId, List<String> changeSubjects) {
    var sql =
        """
        SELECT * FROM workspace_activity_log
        WHERE workspace_id = :workspace_id AND change_subject_id in (:change_subject_ids)
        ORDER BY change_date DESC
      """;
    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_subject_ids", changeSubjects);
    return jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER);
  }
}
