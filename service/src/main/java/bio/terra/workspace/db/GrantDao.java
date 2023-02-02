package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.exception.DuplicateFolderDisplayNameException;
import bio.terra.workspace.db.exception.DuplicateFolderIdException;
import bio.terra.workspace.db.exception.FolderNotFoundException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.grant.GrantData;
import bio.terra.workspace.service.grant.GrantType;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class GrantDao {
  private static final Logger logger = LoggerFactory.getLogger(GrantDao.class);

  /**
   * Query and mapper to collect grant_id from flights where the expire time is passed
   * and there is not a revoke flight already working on revoking this grant.
   */
  private static final String EXPIRED_GRANTS_SQL =
    """
    SELECT grant_id FROM grant WHERE expire_time < now() AND revoke_flight_id IS NULL
    """;

  private static final RowMapper<UUID> GRANT_ID_ROW_MAPPER =
    (rs, rowNum) -> UUID.fromString(rs.getString("grant_id"));

  /**
   * Update to lock a grant
   */
  private static final String LOCK_GRANT_SQL =
    """
    UPDATE grant SET revoke_flight_id = :flight_id
    WHERE grant_id = :grant_id and revoke_flight_id IS NULL
    """;

  /**
   * Update to unlock a grant
   */
  private static final String UNLOCK_GRANT_SQL =
    """
    UPDATE grant SET revoke_flight_id = NULL
    WHERE grant_id = :grant_id and revoke_flight_id = :flight_id
    """;

  /**
   * Delete and implicitly unlock a grant
   */
  private static final String DELETE_GRANT_SQL =
    """
    DELETE FROM grant WHERE grant_id = :grant_id and revoke_flight_id = :flight_id
    """;
  /**
   * Query and mapper to retrieve a grant into GrantData
   */
  private static final String GET_GRANT_SQL =
  """
  SELECT grant_id, workspace_id, user_email, petsa_email, grant_type, resource_id, create_time, expire_time
  FROM grant
  WHERE grant_id = :grant_id 
  """;

  private static final RowMapper<GrantData> GRANT_DATA_ROW_MAPPER =
    (rs, rowNum) ->
      new GrantData(
        UUID.fromString(rs.getString("grant_id")),
        UUID.fromString(rs.getString("workspace_id")),
        rs.getString("user_email"),
        rs.getString("petsa_email"),
        GrantType.fromDb(rs.getString("grant_type")),
        Optional.ofNullable(rs.getString("resource_id")).map(UUID::fromString).orElse(null),
        rs.getString("role"),
        OffsetDateTime.ofInstant(
          rs.getTimestamp("create_time").toInstant(), ZoneId.of("UTC")),
        OffsetDateTime.ofInstant(
          rs.getTimestamp("expire_time").toInstant(), ZoneId.of("UTC")));

  private static final String INSERT_GRANT_SQL =
    """
    INSERT INTO grant 
    (grant_id, workspace_id, user_email, petsa_email, grant_type, 
     resource_id, role, create_time, expire_time)
    VALUES
    (:grant_id, :workspace_id, :user_email, :petsa_email, :grant_type,
     :resource_id, :role, :create_time, :expire_time)
    """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public GrantDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return expired grants that are not already being revoked
   */
  @ReadTransaction
  public List<UUID> getExpiredGrants() {
    var params = new MapSqlParameterSource();
    return jdbcTemplate.query(EXPIRED_GRANTS_SQL, params, GRANT_ID_ROW_MAPPER);
  }

  /**
   * Lock a grant and return its data
   * @param grantId the grant to get
   * @return optional grantData - it is theoretically possible for the grant to be gone
   */
  @WriteTransaction
  public Optional<GrantData> lockAndGetGrant(UUID grantId) {
    var params = new MapSqlParameterSource()
      .addValue("grant_id", grantId.toString());
    int rowsUpdated = jdbcTemplate.update(LOCK_GRANT_SQL, params);
    if (rowsUpdated != 1) {
      return Optional.empty(); // That grant is gone or locked by another flight
    }

    // Since the update returned a row and we are in a serializable transaction,
    // we are guaranteed that the row still exists when we queury it.
    List<GrantData> grantList = jdbcTemplate.query(GET_GRANT_SQL, params, GRANT_DATA_ROW_MAPPER);
    return Optional.of(grantList.get(0));
  }

  /**
   * Unlock the grant, if we hold the lock. Tolerate missing and locked states.
   * @param grantId grant to unlock
   * @param flightId flight that thinks it holds the lock
   */
  @WriteTransaction
  public void unlockGrant(UUID grantId, String flightId) {
    var params = new MapSqlParameterSource()
      .addValue("grant_id", grantId.toString())
      .addValue("flight_id", flightId);
    jdbcTemplate.update(UNLOCK_GRANT_SQL, params);
  }

  /**
   * Delete the grant, if we hold the lock. Throw if the grant is gone
   * or we are not the owning flight.
   * @param grantId grant to delete (and implicitly unlock)
   * @param flightId flight that thinks it holds the lock
   */
  @WriteTransaction
  public void deleteGrant(UUID grantId, String flightId) {
    var params = new MapSqlParameterSource()
      .addValue("grant_id", grantId.toString())
      .addValue("flight_id", flightId);
    int deletedRowCount = jdbcTemplate.update(DELETE_GRANT_SQL, params);
    if (deletedRowCount != 1) {
      throw new InternalLogicException("Grant " + grantId + " locked by flight " + flightId + "is missing or corrupt");
    }
  }

  @WriteTransaction
  public void insertGrant(GrantData grantData) {
    // This is a bit of a pain, but here is what is happening:
    // atZoneSameInstant converts the OffsetDateTime to UTC;
    // then we convert to "local time", which loses the zone info, but gives the result in "local UTC" time
    // then we convert that to timestamp.
    // Gah!
    Timestamp createTimestamp =  Timestamp.valueOf(grantData.createTime().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
    Timestamp expireTimestamp =  Timestamp.valueOf(grantData.expireTime().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());

    var params = new MapSqlParameterSource()
      .addValue("grant_id", grantData.grantId().toString())
      .addValue("workspace_id", grantData.workspaceId().toString())
      .addValue("user_email", grantData.userEmail())
      .addValue("petsa_email", grantData.petSaEmail())
      .addValue("grant_type", grantData.grantType().toDb())
      .addValue("create_time", createTimestamp)
      .addValue("expireTime", expireTimestamp);

    jdbcTemplate.update(INSERT_GRANT_SQL, params);
    logger.info(
      "Inserted record for grant {} for workspace {}",
      grantData.grantId(),
      grantData.workspaceId());
  }
}
