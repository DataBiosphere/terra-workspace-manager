package bio.terra.workspace.db;

import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
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

  // Write a resource into the database from bare parts
  @WriteTransaction
  public void storeResource(
      String workspaceId,
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
    final String sql =
        "INSERT INTO resource (workspace_id, cloud_platform, resource_id, name, description, stewardship_type,"
            + " exact_resource_type, resource_family, cloning_instructions, attributes,"
            + " access_scope, managed_by, associated_app, assigned_user, private_resource_state)"
            + " VALUES (:workspace_id, :cloud_platform, :resource_id, :name, :description, :stewardship_type,"
            + " :exact_resource_type, :resource_family, :cloning_instructions, cast(:attributes AS jsonb),"
            + " :access_scope, :managed_by, :associated_app, :assigned_user, :private_resource_state)";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("cloud_platform", cloudPlatform)
            .addValue("resource_id", resourceId)
            .addValue("name", name)
            .addValue("description", description)
            .addValue("stewardship_type", stewardshipType)
            .addValue("exact_resource_type", resourceType)
            .addValue("resource_family", resourceFamily)
            .addValue("cloning_instructions", cloningInstructions)
            .addValue("attributes", attributes)
            .addValue("access_scope", accessScope)
            .addValue("managed_by", managedBy)
            .addValue("associated_app", associatedApp)
            .addValue("assigned_user", assignedUser)
            .addValue("private_resource_state", privateResourceState);

    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for resource {} for workspace {}", resourceId, workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateResourceException(
          String.format(
              "A resource already exists in the workspace that has the same name (%s) or the same id (%s)",
              name, resourceId));
    }
  }
}
