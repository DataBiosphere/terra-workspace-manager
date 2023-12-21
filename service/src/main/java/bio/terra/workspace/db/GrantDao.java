package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.service.grant.GrantData;
import bio.terra.workspace.service.grant.GrantType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GrantDao {
  /**
   * Record for passing expired grants from the DAO to this service
   *
   * @param grantId
   * @param workspaceId
   */
  public record ExpiredGrant(UUID grantId, UUID workspaceId) {}

  private static final Logger logger = LoggerFactory.getLogger(GrantDao.class);

  /**
   * Query and mapper to collect grant_id from flights where the expire time is passed and there is
   * not a revoke flight already working on revoking this grant.
   */
  private static final String EXPIRED_GRANTS_SQL =
      """
    SELECT grant_id, workspace_id FROM temporary_grant WHERE expire_time < :current_time AND revoke_flight_id IS NULL
    """;

  private static final RowMapper<ExpiredGrant> GRANT_ID_ROW_MAPPER =
      (rs, rowNum) ->
          new ExpiredGrant(
              UUID.fromString(rs.getString("grant_id")),
              UUID.fromString(rs.getString("workspace_id")));

  /** Update to lock a grant */
  private static final String LOCK_GRANT_SQL =
      """
    UPDATE temporary_grant SET revoke_flight_id = :flight_id
    WHERE grant_id = :grant_id AND revoke_flight_id IS NULL
    """;

  /** Update to unlock a grant */
  private static final String UNLOCK_GRANT_SQL =
      """
    UPDATE temporary_grant SET revoke_flight_id = NULL
    WHERE grant_id = :grant_id and revoke_flight_id = :flight_id
    """;

  /** Delete and implicitly unlock a grant */
  private static final String DELETE_GRANT_SQL =
      """
    DELETE FROM temporary_grant WHERE grant_id = :grant_id and revoke_flight_id = :flight_id
    """;

  /** Query and mapper to retrieve a grant into GrantData */
  private static final String GET_GRANT_SQL =
      """
  SELECT grant_id, workspace_id, user_member, petsa_member, grant_type, resource_id, role, create_time, expire_time
  FROM temporary_grant
  WHERE grant_id = :grant_id
  """;

  private static final RowMapper<GrantData> GRANT_DATA_ROW_MAPPER =
      (rs, rowNum) ->
          new GrantData(
              UUID.fromString(rs.getString("grant_id")),
              UUID.fromString(rs.getString("workspace_id")),
              rs.getString("user_member"),
              rs.getString("petsa_member"),
              GrantType.fromDb(rs.getString("grant_type")),
              Optional.ofNullable(rs.getString("resource_id")).map(UUID::fromString).orElse(null),
              rs.getString("role"),
              rs.getTimestamp("create_time").toInstant(),
              rs.getTimestamp("expire_time").toInstant());

  private static final String INSERT_GRANT_SQL =
      """
    INSERT INTO temporary_grant
    (grant_id, workspace_id, user_member, petsa_member, grant_type,
     resource_id, role, create_time, expire_time)
    VALUES
    (:grant_id, :workspace_id, :user_member, :petsa_member, :grant_type,
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
  public List<ExpiredGrant> getExpiredGrants() {
    var params =
        new MapSqlParameterSource().addValue("current_time", Timestamp.from(Instant.now()));

    return jdbcTemplate.query(EXPIRED_GRANTS_SQL, params, GRANT_ID_ROW_MAPPER);
  }

  /**
   * Lock a grant
   *
   * @param grantId the grant to get
   * @return true if we locked the grant; false otherwise (grant no longer exists, already locked)
   */
  @WriteTransaction
  public boolean lockGrant(UUID grantId, String flightId) {
    var params =
        new MapSqlParameterSource()
            .addValue("grant_id", grantId.toString())
            .addValue("flight_id", flightId);
    int rowsUpdated = jdbcTemplate.update(LOCK_GRANT_SQL, params);
    return (rowsUpdated == 1);
  }

  /**
   * Get a grant
   *
   * @param grantId grant to retrieve
   * @return GrantData or null, if not found
   */
  @ReadTransaction
  public @Nullable GrantData getGrant(UUID grantId) {
    var params = new MapSqlParameterSource().addValue("grant_id", grantId.toString());
    List<GrantData> grantList = jdbcTemplate.query(GET_GRANT_SQL, params, GRANT_DATA_ROW_MAPPER);
    if (grantList.size() != 1) {
      return null;
    }
    return grantList.get(0);
  }

  /**
   * Unlock the grant, if we hold the lock. Tolerate missing and locked states.
   *
   * @param grantId grant to unlock
   * @param flightId flight that thinks it holds the lock
   */
  @WriteTransaction
  public void unlockGrant(UUID grantId, String flightId) {
    var params =
        new MapSqlParameterSource()
            .addValue("grant_id", grantId.toString())
            .addValue("flight_id", flightId);
    jdbcTemplate.update(UNLOCK_GRANT_SQL, params);
  }

  /**
   * Delete the grant, if we hold the lock. Throw if the grant is gone or we are not the owning
   * flight.
   *
   * @param grantId grant to delete (and implicitly unlock)
   * @param flightId flight that thinks it holds the lock
   */
  @WriteTransaction
  public void deleteGrant(UUID grantId, String flightId) {
    var params =
        new MapSqlParameterSource()
            .addValue("grant_id", grantId.toString())
            .addValue("flight_id", flightId);
    int deletedRowCount = jdbcTemplate.update(DELETE_GRANT_SQL, params);
    if (deletedRowCount != 1) {
      throw new InternalLogicException(
          "Grant " + grantId + " locked by flight " + flightId + "is missing or corrupt");
    }
    logger.info("Deleted record for grant {}", grantId);
  }

  @WriteTransaction
  public void insertGrant(GrantData grantData) {
    var params =
        new MapSqlParameterSource()
            .addValue("grant_id", grantData.grantId().toString())
            .addValue("workspace_id", grantData.workspaceId().toString())
            .addValue("user_member", grantData.userMember())
            .addValue("petsa_member", grantData.petSaMember())
            .addValue("grant_type", grantData.grantType().toDb())
            .addValue("resource_id", grantData.resourceId())
            .addValue("role", grantData.role())
            .addValue("create_time", Timestamp.from(grantData.createTime()))
            .addValue("expire_time", Timestamp.from(grantData.expireTime()));

    jdbcTemplate.update(INSERT_GRANT_SQL, params);
    logger.info(
        "Inserted record for {} grant {} role {} for workspace {} for {} and {}",
        grantData.grantType(),
        grantData.grantId(),
        grantData.role(),
        grantData.workspaceId(),
        grantData.userMember(),
        grantData.petSaMember());
  }
}
