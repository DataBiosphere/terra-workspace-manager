package bio.terra.TEMPLATE.app.controller;

import bio.terra.TEMPLATE.generated.controller.UnauthenticatedApi;
import bio.terra.TEMPLATE.generated.model.SystemStatus;
import bio.terra.TEMPLATE.generated.model.SystemStatusSystems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
    private int statusCount;

    @Override
    public ResponseEntity<SystemStatus> serviceStatus() {
        // TODO: TEMPLATE: Replace this
        statusCount++;
        boolean status = (statusCount % 2 == 1);
        String reliable = "reliable";
        HttpStatus httpStatus = HttpStatus.OK;

        if (!status) {
            reliable = "unreliable";
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        SystemStatusSystems otherSystemStatus = new SystemStatusSystems()
                .ok(status)
                .addMessagesItem("other systems are SO " + reliable);

        SystemStatus systemStatus = new SystemStatus()
                .ok(status)
                .putSystemsItem("otherSystem", otherSystemStatus);

        return new ResponseEntity<>(systemStatus, httpStatus);
    }

}
