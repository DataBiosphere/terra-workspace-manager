package scripts.disruptivescripts;

import bio.terra.testrunner.common.utils.KubernetesClientUtils;
import bio.terra.testrunner.runner.DisruptiveScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import io.kubernetes.client.openapi.models.V1Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeletePods extends DisruptiveScript {
    private static final Logger logger = LoggerFactory.getLogger(DeletePods.class);

    public DeletePods() {
        super();
        manipulatesKubernetes = true;
    }

    protected static final int secondsToWaitBeforeStartingDisrupt = 15;

    public void disrupt(List<TestUserSpecification> testUsers) throws Exception {
        // give user journey threads time to get started before disruption
        TimeUnit.SECONDS.sleep(secondsToWaitBeforeStartingDisrupt);

        logger.info(
                "Starting disruption - all initially created api pods will be deleted, one by one.");

        List<V1Deployment> allDeployments = KubernetesClientUtils.listDeployments();
        if (allDeployments.size() == 0) {
            throw new RuntimeException("No deployment not found.");
        }

        allDeployments.forEach(deployment -> {
            System.out.println(deployment.getKind() + " " + deployment.getMetadata().getNamespace());
            System.out.println(deployment.getMetadata().getLabels());
        });

        /*V1Deployment apiDeployment = KubernetesClientUtils.getApiDeployment();
        if (apiDeployment == null) {
            throw new RuntimeException("API deployment not found.");
        }

        // get list of api pod names
        String deploymentComponentLabel =
                apiDeployment.getMetadata().getLabels().get(KubernetesClientUtils.apiComponentLabel);
        List<String> podsToDelete = new ArrayList<>();
        KubernetesClientUtils.listPods().stream()
                .filter(
                        pod ->
                                deploymentComponentLabel.equals(
                                        pod.getMetadata().getLabels().get(KubernetesClientUtils.apiComponentLabel)))
                .forEach(p -> podsToDelete.add(p.getMetadata().getName()));

        // delete original pods, and give them a chance to recover
        for (String podName : podsToDelete) {
            logger.debug("delete pod: {}", podName);
            apiDeployment = KubernetesClientUtils.getApiDeployment();
            KubernetesClientUtils.printApiPods(apiDeployment);
            //KubernetesClientUtils.deletePod(podName);
            //KubernetesClientUtils.waitForReplicaSetSizeChange(apiDeployment, podsToDelete.size());
        }*/

        //logger.debug("original pods:");
        //podsToDelete.forEach(p -> logger.debug(p));
        //KubernetesClientUtils.printApiPods(apiDeployment);
    }
}
