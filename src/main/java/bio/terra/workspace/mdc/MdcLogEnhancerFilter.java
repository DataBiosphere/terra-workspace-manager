package bio.terra.workspace.mdc;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import org.hashids.Hashids;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MdcLogEnhancerFilter implements Filter {

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

    String requestId =
        ((requestId = httpRequest.getHeader("Request-Id")) != null)
            ? requestId
            : generateRequestId();

    MDC.put("requestId", requestId + " ");
    filterChain.doFilter(servletRequest, servletResponse);
  }

  private String generateRequestId() {
    Hashids hashids = new Hashids("requestIdSalt");
    long generatedLong = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);
    String id = hashids.encode(generatedLong);

    return id;
  }
}
