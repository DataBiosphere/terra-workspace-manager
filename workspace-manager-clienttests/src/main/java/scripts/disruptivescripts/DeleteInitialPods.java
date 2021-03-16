package scripts.disruptivescripts;

import bio.terra.testrunner.common.utils.KubernetesClientUtils;
import bio.terra.testrunner.runner.DisruptiveScript;
import bio.terra.testrunner.runner.TestRunner;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DeleteInitialPods extends DisruptiveScript {
    private static final Logger logger = LoggerFactory.getLogger(DeleteInitialPods.class);

    public DeleteInitialPods() {
        super();
        manipulatesKubernetes = true;
    }

    protected static final int SECONDS_TO_WAIT_BEFORE_STARTING_DISRUPT = 15;

    @Override
    public void disrupt(List<TestUserSpecification> testUsers) throws Exception {
        // give user journey threads time to get started before disruption
        TimeUnit.SECONDS.sleep(SECONDS_TO_WAIT_BEFORE_STARTING_DISRUPT);

        logger.info("Starting disruption - all initially created api pods will be deleted, one by one.");

        String componentLabelKey = "app.kubernetes.io/component";
        String componentLabelVal = "workspacemanager";
        V1Deployment workspacemanagerDeployment = KubernetesClientUtils.getApiDeployment(componentLabelKey, componentLabelVal);
        if (workspacemanagerDeployment == null) {
            throw new RuntimeException("WorkspaceManager deployment not found.");
        }

        List<String> podsToDelete = KubernetesClientUtils.listPods().stream()
                .filter(
                        pod ->
                                pod.getMetadata().getLabels().containsKey("app.kubernetes.io/component")
                                        && pod.getMetadata().getLabels().get("app.kubernetes.io/component").equals("workspacemanager"))
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());

        // delete original pods, and give them a chance to recover
        for (String podName : podsToDelete) {
            logger.debug("delete pod: {}", podName);
            workspacemanagerDeployment = KubernetesClientUtils.getApiDeployment(componentLabelKey, componentLabelVal);
            if (workspacemanagerDeployment != null) {
                KubernetesClientUtils.printApiPods(workspacemanagerDeployment);
                KubernetesClientUtils.deletePod(podName);
                KubernetesClientUtils.waitForReplicaSetSizeChange(workspacemanagerDeployment, podsToDelete.size());
            }
        }

        logger.debug("original pods:");
        podsToDelete.forEach(p -> logger.debug(p));
        KubernetesClientUtils.printApiPods(workspacemanagerDeployment);
    }
}
