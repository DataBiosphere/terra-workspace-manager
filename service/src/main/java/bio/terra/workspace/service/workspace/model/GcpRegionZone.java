package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * This class holds the region and optional zone string. We use it to hold and validate location
 * inputs.
 */
public class GcpRegionZone {
  private static final Pattern GCP_ZONE_PATTERN =
      Pattern.compile("([a-z]+-[a-z0-9]+)(-([a-z]{1}))?");

  private final String regionPart;
  private final @Nullable String zonePart;

  @JsonCreator
  public GcpRegionZone(
      @JsonProperty("regionPart") String regionPart,
      @JsonProperty("zonePart") @Nullable String zonePart) {
    this.regionPart = regionPart;
    this.zonePart = zonePart;
  }

  /**
   * Parse a location into a region and optional zone component. Throw a validation error if the
   * incoming string is not in a valid format.
   *
   * @param location location input
   * @return GcpRegionZone object
   */
  public static GcpRegionZone fromLocation(String location) {
    Matcher matcher = GCP_ZONE_PATTERN.matcher(location);
    if (!matcher.matches()) {
      throw new ValidationException("Invalid region format");
    }
    List<String> result = new ArrayList<>();
    return new GcpRegionZone(matcher.group(1), matcher.group(3));
  }

  // getters for Jackson
  public String getRegionPart() {
    return regionPart;
  }

  public @Nullable String getZonePart() {
    return zonePart;
  }

  // getters for WSM
  @JsonIgnore
  public String getRegion() {
    return regionPart;
  }

  @JsonIgnore
  public Optional<String> getZone() {
    if (zonePart == null) {
      return Optional.empty();
    }
    return Optional.of(regionPart + "-" + zonePart);
  }
}
