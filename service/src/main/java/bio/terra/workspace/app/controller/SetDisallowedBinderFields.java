package bio.terra.workspace.app.controller;

import org.springframework.core.annotation.Order;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/** https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement */
@ControllerAdvice
@Order
public class SetDisallowedBinderFields {
  @InitBinder
  public void setAllowedFields(WebDataBinder dataBinder) {
    String[] denylist = new String[] {"class.*", "Class.*", "*.class.*", "*.Class.*"};
    dataBinder.setDisallowedFields(denylist);
  }
}
