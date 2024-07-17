package bio.terra.workspace.common.logging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

/** Measures the wall clock time for dispatch of a stairway task (i.e., a flight or a step) */
public class TaskMetrics {
  private final OffsetDateTime startTime;
  private OffsetDateTime endTime;

  public TaskMetrics(OffsetDateTime started) {
    this.startTime = started;
  }

  @JsonCreator
  public TaskMetrics(
      @JsonProperty("startTime") OffsetDateTime startTime,
      @JsonProperty("endTime") OffsetDateTime endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public void setEndTime(OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public OffsetDateTime getEndTime() {
    return this.endTime;
  }

  public OffsetDateTime getStartTime() {
    return this.startTime;
  }

  @Nullable
  public Duration getDuration() {
    if (endTime == null) {
      return null;
    }

    return Duration.between(startTime, endTime);
  }
}
