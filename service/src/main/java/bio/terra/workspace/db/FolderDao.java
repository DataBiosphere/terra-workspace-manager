package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.service.folder.model.Folder;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FolderDao {
  private static final RowMapper<Folder> FOLDER_ROW_MAPPER =
      (rs, rowNum) ->
          new Folder.Builder()
              .id(UUID.fromString(rs.getString("id")))
              .displayName(rs.getString("display_name"))
              .workspaceId(UUID.fromString(rs.getString("workspace_id")))
              .description(rs.getString("description"))
              .parentFolderId(UUID.fromString(rs.getString("parent_folder_id")))
              .build();

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final Logger logger = LoggerFactory.getLogger(FolderDao.class);

  public FolderDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public Folder createFolder(Folder folder) {
    final String sql =
        """
          INSERT INTO folder (workspace_id, id, display_name, description, parent_folder_id)
          values (:workspace_id, :id, :display_name, :description, :parentFolderId)
        """;
    var params =
        new MapSqlParameterSource()
            .addValue("id", folder.getId())
            .addValue("workspace_id", folder.getWorkspaceId())
            .addValue("display_name", folder.getDisplayName())
            .addValue("description", folder.getDescription().orElse(null))
            .addValue("parentFolderId", folder.getParentFolderId().orElse(null));

    try {
      jdbcTemplate.update(sql, params);
    } catch (DuplicateKeyException e) {
      throw new DuplicateFolderDisplayNameException(
          String.format("Folder with display name %s already exists", folder.getDisplayName()));
    }
    return folder;
  }

  @WriteTransaction
  public boolean updateFolder(
      UUID workspaceId,
      UUID folderId,
      @Nullable String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId) {
    var params = new MapSqlParameterSource();
    Optional.ofNullable(displayName).ifPresent(name -> params.addValue("display_name", name));
    Optional.ofNullable(description).ifPresent(d -> params.addValue("description", d));
    Optional.ofNullable(parentFolderId)
        .ifPresent(pfId -> params.addValue("parent_folder_id", pfId));
    StringBuilder sb = new StringBuilder("UPDATE folder SET ");

    sb.append(DbUtils.setColumnsClause(params));

    sb.append(" WHERE workspace_id = :workspace_id AND id = :folder_id");
    params
        .addValue("workspace_id", workspaceId.toString())
        .addValue("folder_id", folderId.toString());
    int rowsAffected = jdbcTemplate.update(sb.toString(), params);
    return rowsAffected > 0;
  }

  @ReadTransaction
  public Folder getFolder(UUID workspaceId, UUID folderId) {
    String sql =
        """
         SELECT workspace_id, id, display_name, description, parent_folder_id
         FROM folder
         WHERE id = :id AND workspace_id = :workspace_id
       """;
    var params = new MapSqlParameterSource();
    params.addValue("id", folderId.toString());
    params.addValue("workspace_id", workspaceId.toString());
    return DataAccessUtils.requiredSingleResult(jdbcTemplate.query(sql, params, FOLDER_ROW_MAPPER));
  }

  @WriteTransaction
  public boolean deleteFolder(UUID workspaceUuid, UUID folderId) {
    final String sql = "DELETE FROM folder WHERE workspace_id = :workspaceId AND id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceUuid.toString())
            .addValue("id", folderId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted record for folder {} in workspace {}", folderId, workspaceUuid);
    } else {
      logger.info("No record found for delete folder {} in workspace {}", folderId, workspaceUuid);
    }
    return deleted;
  }
}
