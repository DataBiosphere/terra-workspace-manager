package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.db.exception.DuplicateFolderIdException;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            parentFolderId,
            DbSerDes.jsonToProperties(Objects.requireNonNull(rs.getString("properties"))));
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
          INSERT INTO folder (workspace_id, id, display_name, description, parent_folder_id, properties)
          values (:workspace_id, :id, :display_name, :description, :parent_folder_id, :properties::jsonb)
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
                    .orElse(DEFAULT_ROOT_FOLDER_ID))
            .addValue("properties", DbSerDes.propertiesToJson(folder.properties()));

    try {
      jdbcTemplate.update(sql, params);
      return folder;
    } catch (DuplicateKeyException e) {
      if (e.getMessage() != null
          && e.getMessage()
              .contains("duplicate key value violates unique constraint \"folder_pkey\"")) {
        throw new DuplicateFolderIdException(
            String.format("Folder id %s already exists", folder.id()));
      }
      if (e.getMessage() != null
          && e.getMessage()
              .contains(
                  "duplicate key value violates unique constraint \"folder_display_name_parent_folder_id_workspace_id_key\"")) {
        throw new DuplicateFolderDisplayNameException(
            String.format(
                "Folder with display name %s already exists in parent folder %s",
                folder.displayName(), folder.parentFolderId()));
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
        && (updateParent == null || !updateParent)) {
      throw new MissingRequiredFieldsException("Must specify at least one field to update.");
    }
    if (parentFolderId != null) {
      if (getFolderIfExists(workspaceId, folderId).isEmpty()) {
        throw new FolderNotFoundException(
            String.format(
                "Cannot update parent folder to %s because it is not found in workspace %s",
                parentFolderId, workspaceId));
      }
    }
    if (isCycle(folderId, parentFolderId)) {
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

  /**
   * If targetFolder is a sub-folder of sourceFolder or equal to sourceFolder, setting targetFolder
   * as the parent of the sourceFolder will form a cycle.
   *
   * @param targetFolder the folder that we want to set as the parent of sourceFolder.
   */
  private boolean isCycle(UUID sourceFolder, @Nullable UUID targetFolder) {
    if (targetFolder == null) return false;
    return listFoldersRecursively(sourceFolder).stream()
        .anyMatch(folder -> targetFolder.equals(folder.id()));
  }

  @ReadTransaction
  public Optional<Folder> getFolderIfExists(UUID workspaceId, UUID folderId) {
    String sql =
        """
           SELECT workspace_id, id, display_name, description, parent_folder_id, properties
           FROM folder
           WHERE id = :id AND workspace_id = :workspace_id
         """;
    var params = new MapSqlParameterSource();
    params.addValue("id", folderId.toString());
    params.addValue("workspace_id", workspaceId.toString());
    try {
      return Optional.of(
          DataAccessUtils.requiredSingleResult(jdbcTemplate.query(sql, params, FOLDER_ROW_MAPPER)));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  @ReadTransaction
  public Folder getFolder(UUID workspaceId, UUID folderId) {
    return getFolderIfExists(workspaceId, folderId)
        .orElseThrow(
            () ->
                new FolderNotFoundException(
                    String.format("Cannot find folder %s in workspace %s", folderId, workspaceId)));
  }

  /** Gets a list of folders in the given workspace */
  public ImmutableList<Folder> listFoldersInWorkspace(UUID workspaceId) {
    String sql =
        """
         SELECT workspace_id, id, display_name, description, parent_folder_id, properties
         FROM folder
         WHERE workspace_id = :workspace_id
       """;
    var params = new MapSqlParameterSource();
    params.addValue("workspace_id", workspaceId.toString());
    return ImmutableList.copyOf(jdbcTemplate.query(sql, params, FOLDER_ROW_MAPPER));
  }

  /**
   * Get the folder tree given root folder using recursive sql query. The root folder is included in
   * the list.
   */
  public ImmutableList<Folder> listFoldersRecursively(UUID rootFolderId) {
    String sql =
        """
         WITH RECURSIVE subfolders AS (
                 SELECT
                         workspace_id, id, display_name, description, parent_folder_id, properties
                 FROM
                         folder
                 WHERE
                         id = :root_folder_id
                 UNION
                         SELECT
                                 e.workspace_id, e.id, e.display_name, e.description, e.parent_folder_id, e.properties
                         FROM
                                 folder e
                         INNER JOIN subfolders s ON s.id = e.parent_folder_id
         ) SELECT
                 *
         FROM
                 subfolders;
       """;
    var params = new MapSqlParameterSource();
    params.addValue("root_folder_id", rootFolderId.toString());
    return ImmutableList.copyOf(jdbcTemplate.query(sql, params, FOLDER_ROW_MAPPER));
  }

  /**
   * Delete a folder and all of its sub-folders recursively.
   *
   * @param folderId the folder where the deletion starts.
   * @return true if folder(s) are deleted.
   */
  @WriteTransaction
  public boolean deleteFolderRecursive(UUID workspaceUuid, UUID folderId) {

    final String sql =
        """
WITH RECURSIVE subfolders AS (
        SELECT
                id,
                parent_folder_id
        FROM
                folder
        WHERE
                id = :source_folder_id
        UNION
                SELECT
                        e.id,
                        e.parent_folder_id
                FROM
                        folder e
                INNER JOIN subfolders s ON s.id = e.parent_folder_id
) DELETE FROM folder WHERE id IN (SELECT id FROM subfolders);
""";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("source_folder_id", folderId.toString());
    boolean rowsAffected = jdbcTemplate.update(sql, params) > 0;

    if (rowsAffected) {
      logger.info("Deleted record for folder {} in workspace {}", folderId, workspaceUuid);
    } else {
      logger.info("No record found for delete folder {} in workspace {}", folderId, workspaceUuid);
    }
    return rowsAffected;
  }

  @WriteTransaction
  public void updateFolderProperties(
      UUID workspaceUuid, UUID folderId, Map<String, String> properties) {
    if (properties.isEmpty()) {
      throw new MissingRequiredFieldsException("No folder property is specified to update");
    }
    Map<String, String> updatedProperties =
        new HashMap<>(getFolderProperties(workspaceUuid, folderId));
    updatedProperties.putAll(properties);
    storeFolderProperties(updatedProperties, workspaceUuid, folderId);
  }

  @WriteTransaction
  public void deleteFolderProperties(UUID workspaceUuid, UUID folderId, List<String> propertyKeys) {
    if (propertyKeys.isEmpty()) {
      throw new MissingRequiredFieldsException("No folder property is specified to delete");
    }
    Map<String, String> properties = new HashMap<>(getFolderProperties(workspaceUuid, folderId));
    for (String key : propertyKeys) {
      properties.remove(key);
    }
    storeFolderProperties(properties, workspaceUuid, folderId);
  }

  /** Update the properties column of a given folder in a given workspace. */
  private void storeFolderProperties(
      Map<String, String> properties, UUID workspaceUuid, UUID folderId) {
    final String sql =
        """
          UPDATE folder SET properties = cast(:properties AS jsonb)
          WHERE workspace_id = :workspace_id AND id = :folder_id
        """;

    var params = new MapSqlParameterSource();
    params
        .addValue("properties", DbSerDes.propertiesToJson(properties))
        .addValue("workspace_id", workspaceUuid.toString())
        .addValue("folder_id", folderId.toString());
    jdbcTemplate.update(sql, params);
  }

  private ImmutableMap<String, String> getFolderProperties(UUID workspaceUuid, UUID folderId) {
    String selectPropertiesSql =
        """
          SELECT properties FROM folder
          WHERE workspace_id = :workspace_id AND id = :folder_id
        """;
    MapSqlParameterSource propertiesParams =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("folder_id", folderId.toString());
    String result;

    try {
      result = jdbcTemplate.queryForObject(selectPropertiesSql, propertiesParams, String.class);
    } catch (EmptyResultDataAccessException e) {
      throw new FolderNotFoundException(
          String.format("Cannot find resource %s in workspace %s.", folderId, workspaceUuid));
    }
    return result == null
        ? ImmutableMap.of()
        : ImmutableMap.copyOf(DbSerDes.jsonToProperties(result));
  }
}
