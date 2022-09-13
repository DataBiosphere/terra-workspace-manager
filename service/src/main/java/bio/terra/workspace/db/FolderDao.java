package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.db.exception.DuplicateFolderIdException;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FolderDao {
  // Despite there's no default root folder entry in the `folder` table,
  // parent folder id must be non-null even for top-level folder to
  // guarantee display_name uniqueness at the same folder level.
  private static final String DEFAULT_ROOT_FOLDER_ID = "0";
  private static final RowMapper<Folder> FOLDER_ROW_MAPPER =
      (rs, rowNum) -> {
        var parentFolderIdString = Objects.requireNonNull(rs.getString("parent_folder_id"));
        UUID parentFolderId =
            DEFAULT_ROOT_FOLDER_ID.equals(parentFolderIdString)
                ? null
                : UUID.fromString(parentFolderIdString);
        return new Folder(
            UUID.fromString(Objects.requireNonNull(rs.getString("id"))),
            Objects.requireNonNull(UUID.fromString(rs.getString("workspace_id"))),
            Objects.requireNonNull(rs.getString("display_name")),
            rs.getString("description"),
            parentFolderId);
      };

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
          values (:workspace_id, :id, :display_name, :description, :parent_folder_id)
        """;
    if (folder.parentFolderId() != null) {
      Optional<Folder> parentFolder =
          getFolderIfExists(folder.workspaceId(), folder.parentFolderId());
      if (parentFolder.isEmpty()) {
        throw new FolderNotFoundException(
            String.format(
                "Failed to find parent folder %s in workspace %s",
                folder.parentFolderId(), folder.workspaceId()));
      }
    }
    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", folder.workspaceId().toString())
            .addValue("id", folder.id().toString())
            .addValue("display_name", folder.displayName())
            .addValue("description", folder.description())
            .addValue(
                "parent_folder_id",
                Optional.ofNullable(folder.parentFolderId())
                    .map(UUID::toString)
                    .orElse(DEFAULT_ROOT_FOLDER_ID));

    try {
      jdbcTemplate.update(sql, params);
      return folder;
    } catch (DuplicateKeyException e) {
      if (e.getMessage() != null
          && e.getMessage()
              .contains("duplicate key value violates unique constraint \"folder_pkey\"")) {
        throw new DuplicateFolderIdException("Folder id is duplicated");
      }
      if (e.getMessage() != null
          && e.getMessage()
              .contains(
                  "duplicate key value violates unique constraint \"folder_display_name_parent_folder_id_workspace_id_key\"")) {
        throw new DuplicateFolderDisplayNameException(
            String.format("Folder with display name %s already exists", folder.displayName()));
      }
      throw e;
    } catch (DataIntegrityViolationException e) {
      if (e.getMessage() != null
          && e.getMessage().contains("violates foreign key constraint \"fk_folder_wid\"")) {
        throw new WorkspaceNotFoundException(
            String.format(
                "Failed to find workspace %s in which to create the folder", folder.workspaceId()));
      }
      throw e;
    }
  }

  @WriteTransaction
  public boolean updateFolder(
      UUID workspaceId,
      UUID folderId,
      @Nullable String displayName,
      @Nullable String description,
      @Nullable UUID parentFolderId,
      @Nullable Boolean updateParent) {
    if (displayName == null
        && description == null
        && parentFolderId == null
        && updateParent == null) {
      throw new MissingRequiredFieldException("Must specified fields to update");
    }
    if (parentFolderId != null) {
      if (getFolderIfExists(workspaceId, folderId).isEmpty()) {
        throw new FolderNotFoundException(
            String.format(
                "Cannot update parent folder to %s because it is not found in workspace %s",
                parentFolderId, workspaceId));
      }
    }
    if (canFormCyclicCycle(workspaceId, parentFolderId, folderId)) {
      throw new BadRequestException(
          String.format(
              "Cannot update parent folder id to %s as it will create a cycle", parentFolderId));
    }
    var params = new MapSqlParameterSource();
    Optional.ofNullable(displayName).ifPresent(name -> params.addValue("display_name", name));
    Optional.ofNullable(description).ifPresent(d -> params.addValue("description", d));
    if (Boolean.TRUE.equals(updateParent)) {
      params.addValue(
          "parent_folder_id",
          Optional.ofNullable(parentFolderId).map(UUID::toString).orElse(DEFAULT_ROOT_FOLDER_ID));
    }
    StringBuilder sb = new StringBuilder("UPDATE folder SET ");

    sb.append(DbUtils.setColumnsClause(params));

    sb.append(" WHERE workspace_id = :workspace_id AND id = :folder_id");
    params
        .addValue("workspace_id", workspaceId.toString())
        .addValue("folder_id", folderId.toString());
    try {
      int rowsAffected = jdbcTemplate.update(sb.toString(), params);
      return rowsAffected > 0;
    } catch (DuplicateKeyException e) {
      if (e.getMessage() != null
          && e.getMessage()
              .contains(
                  "duplicate key value violates unique constraint \"folder_display_name_parent_folder_id_workspace_id_key\"")) {
        throw new DuplicateFolderDisplayNameException(
            String.format(
                "Fails to update due to duplicate display name at the same folder level"));
      }
      throw e;
    }
  }

  private boolean canFormCyclicCycle(UUID workspaceId, UUID folder1, UUID folder2) {
    if (folder1 == null) {
      return false;
    }
    if (folder1.equals(folder2)) {
      return true;
    }
    Folder firstFolder = getFolder(workspaceId, folder1);
    return canFormCyclicCycle(workspaceId, firstFolder.parentFolderId(), folder2);
  }

  @ReadTransaction
  public Optional<Folder> getFolderIfExists(UUID workspaceId, UUID folderId) {
    try {
      return Optional.of(getFolder(workspaceId, folderId));
    } catch (FolderNotFoundException e) {
      return Optional.empty();
    }
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
    try {
      return DataAccessUtils.requiredSingleResult(
          jdbcTemplate.query(sql, params, FOLDER_ROW_MAPPER));
    } catch (EmptyResultDataAccessException e) {
      throw new FolderNotFoundException(
          String.format("Cannot find folder %s in workspace %s", folderId, workspaceId));
    }
  }

  /**
   * Gets a list of folders.
   *
   * @param parentFolderId when null, get all the folders in a workspace, otherwise, get the direct
   *     subfolders of a given parent folder.
   */
  public ImmutableList<Folder> listFolders(UUID workspaceId, @Nullable UUID parentFolderId) {
    String sql =
        """
         SELECT workspace_id, id, display_name, description, parent_folder_id
         FROM folder
         WHERE workspace_id = :workspace_id
       """;
    var params = new MapSqlParameterSource();
    params.addValue("workspace_id", workspaceId.toString());
    if (parentFolderId != null) {
      sql += " AND parent_folder_id = :parent_folder_id";
      params.addValue("parent_folder_id", parentFolderId.toString());
    }
    return ImmutableList.copyOf(jdbcTemplate.query(sql, params, FOLDER_ROW_MAPPER));
  }

  @WriteTransaction
  public boolean deleteFolder(UUID workspaceUuid, UUID folderId) {
    ImmutableList<Folder> subFolders = listFolders(workspaceUuid, folderId);
    boolean deleted = false;
    for (Folder folder : subFolders) {
      deleted |= deleteFolder(workspaceUuid, folder.id());
    }
    final String sql = "DELETE FROM folder WHERE workspace_id = :workspaceId AND id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceUuid.toString())
            .addValue("id", folderId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    deleted |= rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted record for folder {} in workspace {}", folderId, workspaceUuid);
    } else {
      logger.info("No record found for delete folder {} in workspace {}", folderId, workspaceUuid);
    }
    return deleted;
  }
}
