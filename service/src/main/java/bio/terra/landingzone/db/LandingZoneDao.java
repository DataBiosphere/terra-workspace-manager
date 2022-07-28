package bio.terra.landingzone.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.landingzone.db.exception.DuplicateLandingZoneException;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.landingzone.sevice.model.LandingZone;
import bio.terra.workspace.db.DbSerDes; // TODO move this utility to terra common.db.
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** LandingZoneDao includes operations on the landing zone tables. */
@Component
public class LandingZoneDao {
  /** SQL query for reading landing zone records. */
  private static final String LANDINGZONE_SELECT_SQL =
      "SELECT landingzone_id, resource_group, definition_id, definition_version_id, display_name, description, properties"
          + " FROM landingzone";

  private final Logger logger = LoggerFactory.getLogger(LandingZoneDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public LandingZoneDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Persists a landing zone record to DB. Returns ID of persisted landing zone on success.
   *
   * @param landingzone all properties of the landing zone to create
   * @return landingzone id
   */
  @WriteTransaction
  public UUID createLandingZone(LandingZone landingzone) {
    final String sql =
        "INSERT INTO landingzone (landingzone_id, resource_group, definition_id, definition_version_id, display_name, description, properties) "
            + "values (:landingzone_id, :resource_group, :definition_id, :definition_version_id, :display_name, :description"
            + " cast(:properties AS jsonb))";

    final String landingZoneUuid = landingzone.getLandingZoneId().toString();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("landingzone_id", landingZoneUuid)
            .addValue("resource_group", landingzone.getResourceGroupId())
            .addValue("definition_id", landingzone.getDefinition().orElse(null))
            .addValue("definition_version_id", landingzone.getVersion().orElse(null))
            .addValue("display_name", landingzone.getDisplayName().orElse(null))
            .addValue("description", landingzone.getDescription().orElse(null))
            .addValue("properties", DbSerDes.propertiesToJson(landingzone.getProperties()));
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for landing zone {}", landingZoneUuid);
    } catch (DuplicateKeyException e) {
      if (e.getMessage()
          .contains("duplicate key value violates unique constraint \"landingzone_pkey\"")) {
        // Landing Zone record with landingzone_id already exists.
        throw new DuplicateLandingZoneException(
            String.format(
                "Landing Zone with id %s already exists - display name %s definition %s version %s",
                landingZoneUuid,
                landingzone.getDisplayName().toString(),
                landingzone.getDefinition().toString(),
                landingzone.getVersion().toString()),
            e);
      } else {
        throw e;
      }
    }
    return landingzone.getLandingZoneId();
  }

  /**
   * @param landingZoneUuid unique identifier of the landing zone
   * @return true on successful delete, false if there's nothing to delete
   */
  @WriteTransaction
  public boolean deleteLandingZone(UUID landingZoneUuid) {
    final String sql = "DELETE FROM landingzone WHERE landingzone_id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", landingZoneUuid.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted record for landing zone {}", landingZoneUuid);
    } else {
      logger.info("No record found for delete landing zone {}", landingZoneUuid);
    }

    return deleted;
  }

  /**
   * Retrieves a landing zone from database by ID.
   *
   * @param uuid unique identifier of the landing zone
   * @return landing zone value object
   */
  public LandingZone getLandingZone(UUID uuid) {
    return getLandingZoneIfExists(uuid)
        .orElseThrow(
            () ->
                new LandingZoneNotFoundException(
                    String.format("Landing Zone %s not found.", uuid.toString())));
  }

  @ReadTransaction
  public Optional<LandingZone> getLandingZoneIfExists(UUID uuid) {
    if (uuid == null) {
      throw new MissingRequiredFieldException("Valid landing zone id is required");
    }
    String sql = LANDINGZONE_SELECT_SQL + " WHERE landingzone_id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", uuid.toString());
    try {
      LandingZone result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(sql, params, LANDINGZONE_ROW_MAPPER));
      logger.info("Retrieved landing zone record {}", result);
      return Optional.of(result);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  private static final RowMapper<LandingZone> LANDINGZONE_ROW_MAPPER =
      (rs, rowNum) ->
          LandingZone.builder()
              .landingZoneId(UUID.fromString(rs.getString("landingzone_id")))
              .resourceGroupId(rs.getString("resource_group"))
              .definition(rs.getString("definition_id"))
              .version(rs.getString("definition_version_id"))
              .displayName(rs.getString("display_name"))
              .description(rs.getString("description"))
              .properties(
                  Optional.ofNullable(rs.getString("properties"))
                      .map(DbSerDes::jsonToProperties)
                      .orElse(null))
              .build();
}
