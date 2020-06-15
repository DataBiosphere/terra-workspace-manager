package bio.terra.workspace.app.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiResourceConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/swagger-webjar/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/3.25.4/");
    registry.addResourceHandler("/**").addResourceLocations("classpath:/api/");
  }
}
