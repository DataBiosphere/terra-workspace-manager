package bio.terra.workspace.app.configuration;

import brave.SpanCustomizer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiResourceConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**").addResourceLocations("classpath:/api/");
  }

  @Autowired private SpanCustomizer spanCustomizer;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler)
              throws Exception {
            String mdcRequestId = request.getHeader("X-Request-ID");
            if (mdcRequestId != null) spanCustomizer.tag("mdc-request-id", mdcRequestId);

            String mdcCorrelationId = request.getHeader("X-Correlation-ID");
            if (mdcCorrelationId != null)
              spanCustomizer.tag("mdc-correlation-id", mdcCorrelationId);
            // add tags to Stackdriver traces here

            return true;
          }
        });
  }
}
