package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toDateTime;
import static bio.terra.workspace.service.resource.controlled.GcsApiConversions.toOffsetDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.common.BaseUnitTest;
import com.google.api.client.util.DateTime;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class GcsApiConversionsTest extends BaseUnitTest {
  private static final OffsetDateTime OFFSET_DATE_TIME_1 =
      OffsetDateTime.parse("2017-12-03T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  private static final OffsetDateTime OFFSET_DATE_TIME_2 =
      OffsetDateTime.parse("2017-12-03T10:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  private static final DateTime DATE_TIME_1 = DateTime.parseRfc3339("1985-04-12T23:20:50.52Z");
  private static final DateTime DATE_TIME_2 = DateTime.parseRfc3339("1996-12-19T16:39:57-08:00");

  @Test
  public void testToDateTime() {
    assertNull(toDateTime(null));

    final DateTime dateTime1 = toDateTime(OFFSET_DATE_TIME_1);
    assertEquals(60, dateTime1.getTimeZoneShift());
    assertEquals(OFFSET_DATE_TIME_1.toInstant().toEpochMilli(), dateTime1.getValue());

    final DateTime dateTime2 = toDateTime(OFFSET_DATE_TIME_2);
    assertEquals(Duration.ofHours(-5).toMinutes(), dateTime2.getTimeZoneShift());
    assertEquals(OFFSET_DATE_TIME_2.toInstant().toEpochMilli(), dateTime2.getValue());
  }

  @Test
  public void testToOffsetDateTime() {
    assertNull(toOffsetDateTime(null));

    final OffsetDateTime offsetDateTime1 = toOffsetDateTime(DATE_TIME_1);
    assertEquals(DATE_TIME_1.getValue(), offsetDateTime1.toInstant().toEpochMilli());
    assertEquals(ZoneOffset.UTC, offsetDateTime1.getOffset());

    final OffsetDateTime offsetDateTime2 = toOffsetDateTime(DATE_TIME_2);
    assertEquals(DATE_TIME_2.getValue(), offsetDateTime2.toInstant().toEpochMilli());
    assertEquals(ZoneOffset.ofHours(-8), offsetDateTime2.getOffset());
  }

  @Test
  public void testRoundTrip() {
    final OffsetDateTime offsetDateTime1 = toOffsetDateTime(toDateTime(OFFSET_DATE_TIME_1));
    assertEquals(OFFSET_DATE_TIME_1, offsetDateTime1);

    final DateTime dateTime2 = toDateTime(toOffsetDateTime(DATE_TIME_2));
    assertEquals(DATE_TIME_2, dateTime2);
  }
}
