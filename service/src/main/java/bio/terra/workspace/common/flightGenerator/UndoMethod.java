package bio.terra.workspace.common.flightGenerator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a method as not having an undo method. The value is the name of
 * the undo method. The signature of the undo method must match the signature of the method being
 * annotated.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UndoMethod {
  String value();
}
