package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FolderAndResourceDao {

  NamedParameterJdbcTemplate jdbcTemplate;
  public FolderAndResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public boolean addResourceToFolder(UUID resourceId, UUID folderId) {
    final String sql =
        """
          INSERT INTO folder_and_resource (folder_id, resource_id)
          values (:folder_id, :resource_id)
        """;
    var params = new MapSqlParameterSource()
        .addValue("folder_id", folderId.toString())
        .addValue("resource_id", resourceId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    return rowsAffected > 0;
  }

  @ReadTransaction
  public List<UUID> getResourceIdsInFolder(UUID folderId) {
    String sql =
        """
         SELECT resource_id FROM folder_and_resource
         WHERE folder_id = :folder_id
       """;
    var params = new MapSqlParameterSource()
        .addValue("folder_id", folderId.toString());
    return jdbcTemplate.query(sql, params, (rs, rowNum) -> UUID.fromString(rs.getString("resource_id")));
  }

  @ReadTransaction
  public List<UUID> getFolderIdsForResource(UUID resourceId) {
    String sql =
        """
         SELECT resource_id FROM folder_and_resource
         WHERE resource_id = :resource_id
       """;
    var params = new MapSqlParameterSource()
        .addValue("resource_id", resourceId.toString());
    return jdbcTemplate.query(sql, params, (rs, rowNum) -> UUID.fromString(rs.getString("folder_id")));
  }

  @WriteTransaction
  public boolean removeResourceFromFolder(UUID folderId, UUID resourceId) {
    final String sql =
        """
          DELETE FROM folder_and_resource
          WHERE folder_id = :folder_Id AND resource_id = :resource_id
        """;
    var params = new MapSqlParameterSource()
        .addValue("resource_id", resourceId)
        .addValue("folder_id", folderId);
    var updatedRow = jdbcTemplate.update(sql, params);
    return updatedRow > 0;
  }

}
