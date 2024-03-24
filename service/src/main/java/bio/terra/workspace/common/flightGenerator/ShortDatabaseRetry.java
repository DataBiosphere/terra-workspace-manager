package bio.terra.workspace.common.flightGenerator;

@FixedIntervalRetry(intervalSeconds = 1, maxCount = 5)
public @interface ShortDatabaseRetry {}
