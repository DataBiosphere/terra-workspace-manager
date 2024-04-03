package bio.terra.workspace.common.stairway;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface StepInput {
  String USE_DEFAULT_NAME = "";

  String value() default USE_DEFAULT_NAME;
}
