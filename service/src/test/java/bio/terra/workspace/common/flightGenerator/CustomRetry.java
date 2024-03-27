package bio.terra.workspace.common.flightGenerator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@FixedIntervalRetry(intervalSeconds = 5, maxCount = 3)
public @interface CustomRetry {

}
