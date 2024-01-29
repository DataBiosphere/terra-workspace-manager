package bio.terra.workspace.app.configuration.spring;

import bio.terra.workspace.app.StartupInitializer;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class BeanConfig {

  @Bean("jdbcTemplate")
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(
      WorkspaceDatabaseConfiguration config) {
    return new NamedParameterJdbcTemplate(config.getDataSource());
  }

  public static class HTMLCharacterEscapes extends CharacterEscapes {
    private static final int[] asciiEscapes;

    static {
      // Start with a copy of the default set of escaped characters, then modify.
      asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
      // Escape HTML metacharacters for security reasons. For JSON, CharacterEscapes.ESCAPE_STANDARD
      // means unicode-escaping.
      asciiEscapes['<'] = CharacterEscapes.ESCAPE_STANDARD;
      asciiEscapes['>'] = CharacterEscapes.ESCAPE_STANDARD;
      asciiEscapes['&'] = CharacterEscapes.ESCAPE_STANDARD;
    }

    /** Return the escape codes used for ASCII characters. */
    @Override
    public int[] getEscapeCodesForAscii() {
      return asciiEscapes;
    }

    /**
     * Return the escape codes used for non-ASCII characters, or ASCII characters with escape code
     * set to ESCAPE_CUSTOM.
     *
     * <p>This is required because CharacterEscapes is abstract. We don't need additional escaping
     * for any characters, so per documentation this can return null for all inputs.
     */
    @Override
    public SerializableString getEscapeSequence(int ch) {
      return null;
    }
  }

  @Bean("objectMapper")
  public ObjectMapper objectMapper() {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            // Disable serialization Date as Timestamp so swagger shows date in the RFC3339
            // format, see details in PF-1855.
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setDefaultPropertyInclusion(Include.NON_ABSENT);
    objectMapper.getFactory().setCharacterEscapes(new HTMLCharacterEscapes());
    return objectMapper;
  }

  // This is a "magic bean": It supplies a method that Spring calls after the application is setup,
  // but before the port is opened for business. That lets us do database migration and stairway
  // initialization on a system that is otherwise fully configured. The rule of thumb is that all
  // bean initialization should avoid database access. If there is additional database work to be
  // done, it should happen inside this method.
  @Bean
  public SmartInitializingSingleton postSetupInitialization(ApplicationContext applicationContext) {
    return () -> StartupInitializer.initialize(applicationContext);
  }
}
