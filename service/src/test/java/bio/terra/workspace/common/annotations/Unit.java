package bio.terra.workspace.common.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** An annotation for unit tests to ensure that the right tags and profiles are used. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@BaseTest
@ActiveProfiles("unit-test")
@Tag("unit")
public @interface Unit {}
