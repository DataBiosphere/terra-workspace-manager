package bio.terra.TEMPLATE.app.controller;

import bio.terra.TEMPLATE.generated.controller.TemplateApi;
import bio.terra.TEMPLATE.service.ping.PingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;



@Controller
public class TemplateApiController implements TemplateApi {
    private PingService pingService;

    @Autowired
    public TemplateApiController(PingService pingService) {
        this.pingService = pingService;
    }

    @Override
    public ResponseEntity<String> ping(@RequestParam(value = "message", required = false) String message) {
        String result = pingService.computePing(message);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
