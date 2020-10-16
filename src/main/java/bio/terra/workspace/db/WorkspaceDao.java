package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public WorkspaceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public String createWorkspace(UUID workspaceId, UUID spendProfile) {
    String sql =
        "INSERT INTO workspace (workspace_id, spend_profile, profile_settable) values "
            + "(:id, :spend_profile, :spend_profile_settable)";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId.toString());
    paramMap.put("spend_profile", spendProfile);
    paramMap.put("spend_profile_settable", spendProfile == null);

    try {
      jdbcTemplate.update(sql, paramMap);
    } catch (DuplicateKeyException e) {
      throw new DuplicateWorkspaceException(
          "Workspace " + workspaceId.toString() + " already exists.", e);
    }
    return workspaceId.toString();
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean deleteWorkspace(UUID workspaceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", workspaceId.toString());
    int rowsAffected =
        jdbcTemplate.update("DELETE FROM workspace WHERE workspace_id = :id", paramMap);
    return rowsAffected > 0;
  }

  public WorkspaceDescription getWorkspace(UUID id) {
    String sql = "SELECT * FROM workspace where workspace_id = (:id)";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", id.toString());

    try {
      Map<String, Object> queryOutput = jdbcTemplate.queryForMap(sql, paramMap);

      WorkspaceDescription desc = new WorkspaceDescription();
      desc.setId(UUID.fromString(queryOutput.get("workspace_id").toString()));

      if (queryOutput.getOrDefault("spend_profile", null) == null) {
        desc.setSpendProfile(null);
      } else {
        desc.setSpendProfile(UUID.fromString(queryOutput.get("spend_profile").toString()));
      }

      return desc;
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException("Workspace not found.");
    }
  }
}
