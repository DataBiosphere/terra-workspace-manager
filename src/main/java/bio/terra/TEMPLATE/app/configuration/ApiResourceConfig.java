package bio.terra.TEMPLATE.app.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiResourceConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/swagger-webjar/**").addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/3.24.0/");
    registry.addResourceHandler("/api/**").addResourceLocations("classpath:/api/");
  }
}
