package scripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CreateDeploymentRequest;
import bio.terra.workspace.model.GetDeployment;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.testscripts.WorkspaceLifecycle;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceApiTestScriptBase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Disabled
public class AzureComputeLifecycle extends WorkspaceApiTestScriptBase {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceLifecycle.class);
    private static final String workspaceName = "name";
    private static final String workspaceDescriptionString = "description";

    @Override
    protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi) throws Exception {
        UUID workspaceId = UUID.randomUUID();
        String deploymentName = "test" + UUID.randomUUID();
        String template;

        try (InputStream stream =
                     getClass().getClassLoader().getResourceAsStream("azuredeploy.json")) {
            template = IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Problem reading default template", e);
        }

        CreateDeploymentRequest request = new CreateDeploymentRequest();
        request.setDeploymentName(deploymentName);
        request.setTemplate(template);

        workspaceApi.createDeployment(request);
        ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE deployment");

        //The template usually deploys in seconds, providing ample buffer
        Thread.sleep(60000);

        GetDeployment deployment = workspaceApi.getDeployment(deploymentName);
        ClientTestUtils.assertHttpSuccess(workspaceApi, "GET deployment");

        assertThat(deployment.getName(), equalTo(deploymentName));
        assertThat(deployment.getProvisioningState(), equalTo("Succeeded"));
    }
}
