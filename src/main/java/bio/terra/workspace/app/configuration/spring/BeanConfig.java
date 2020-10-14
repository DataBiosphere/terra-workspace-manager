package bio.terra.workspace.app.configuration.spring;

import bio.terra.workspace.app.StartupInitializer;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.opencensus.contrib.spring.aop.CensusSpringAspect;
import io.opencensus.contrib.spring.instrument.web.client.TracingAsyncClientHttpRequestInterceptor;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class BeanConfig {
  @Bean
  public Tracer tracer() {
    return Tracing.getTracer();
  }

  @Bean
  public TracingAsyncClientHttpRequestInterceptor requestInterceptor() {
    return TracingAsyncClientHttpRequestInterceptor.create(null, null);
  }

  @Bean
  public CensusSpringAspect censusAspect() {
    return new CensusSpringAspect(tracer());
  }

  @Bean("jdbcTemplate")
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(
      WorkspaceDatabaseConfiguration config) {
    return new NamedParameterJdbcTemplate(config.getDataSource());
  }

  @Bean("objectMapper")
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new ParameterNamesModule())
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .setDefaultPropertyInclusion(Include.NON_ABSENT);
  }

  // This is a "magic bean": It supplies a method that Spring calls after the application is setup,
  // but before the port is opened for business. That lets us do database migration and stairway
  // initialization on a system that is otherwise fully configured. The rule of thumb is that all
  // bean initialization should avoid database access. If there is additional database work to be
  // done, it should happen inside this method.
  @Bean
  public SmartInitializingSingleton postSetupInitialization(ApplicationContext applicationContext) {
    return () -> {
      StartupInitializer.initialize(applicationContext);
    };
  }
}
