package bio.terra.workspace.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"bio.terra.workspace", "bio.terra.common.migrate"})
@EnableScheduling
public class Main {
  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
