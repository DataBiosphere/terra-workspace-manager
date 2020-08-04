package automation.plugins.jmeter.functions;

import com.google.auth.oauth2.GoogleCredentials;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class OAuth2Token extends AbstractFunction {
    private static final Logger log = LoggerFactory.getLogger(OAuth2Token.class);
    private static final List<String> desc = new LinkedList<>();

    private static final String KEY = "__OAuth2Token"; //$NON-NLS-1$

    // Number of parameters expected - used to reject invalid calls
    private static final int MIN_PARAMETER_COUNT = 3;
    private static final int MAX_PARAMETER_COUNT = 4;

    static {
        desc.add(getResString("oauth2_provider")); //$NON-NLS-1$
        desc.add(getResString("oauth2_service_accountkey_env_or_configpath")); //$NON-NLS-1$
        desc.add(getResString("oauth2_user_email")); //$NON-NLS-1$
        desc.add(getResString("function_name_paropt")); //$NON-NLS-1$
    }

    private Object[] values;

    public OAuth2Token() {

    }

    @Override
    public List<String> getArgumentDesc() {
        return desc;
    }

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) throws InvalidVariableException {
        String authToken = null;

        JMeterVariables vars = getVariables();

        CloudProvider cloudProvider = CloudProvider.fromLabel(((CompoundVariable) values[0]).execute().trim());

        String saKeyEnvVarOrConfigPath = ((CompoundVariable) values[1]).execute().trim();
        String userEmail = System.getenv(((CompoundVariable) values[2]).execute().trim());
        byte[] saKey = getBytesFromEnvVarOrConfigPath(saKeyEnvVarOrConfigPath);

        switch (cloudProvider) {
            case GCP:
                GoogleCredentials credentials = null;
                try {
                    credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(saKey))
                            .createScoped(new ArrayList<String>() {
                                private static final long serialVersionUID = 2644360116969700916L;
                                {
                                    add("profile");
                                    add("email");
                                    add("openid");
                                }
                            }).createDelegated(userEmail);
                    credentials.refreshIfExpired();
                    authToken = credentials.getAccessToken().getTokenValue();
                } catch (IOException e) {
                    log.warn(e.getMessage());
                    throw new RuntimeException(e.getMessage());
                }
                break;
            case AWS:
            case AZURE:
                log.warn(cloudProvider.getDescription() + " not implemented");
                throw new RuntimeException(cloudProvider.getDescription() + " not implemented");
            default:
                log.warn(cloudProvider.getDescription());
                throw new RuntimeException(cloudProvider.getDescription());
        }

        if (StringUtils.isEmpty(authToken)) {
            log.warn("Unable to obtain oauth2 token for user '{}'", userEmail);
            throw new RuntimeException(String.format("Unable to obtain oauth2 token for user '%s'", userEmail));
        }

        String varName = null;

        if (values.length > 3)
            varName = ((CompoundVariable) values[3]).execute().trim();

        if (vars != null && varName != null) {
            vars.put(varName, authToken);
        }

        return authToken;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, MIN_PARAMETER_COUNT, MAX_PARAMETER_COUNT);
        values = parameters.toArray();
    }

    @Override
    public String getReferenceKey() {
        return KEY;
    }

    private static String getResString(String key) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages");
        return bundle.containsKey(key) ? bundle.getString(key) : String.format("[res_key=\"%s\"]", key);
    }

    private static byte[] getBytesFromEnvVarOrConfigPath(String env) {
        String envVarOrConfigPath = System.getenv(env);
        File f = new File(envVarOrConfigPath);
        if (f.exists()) {
            try {
                return Files.readAllBytes(f.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return envVarOrConfigPath.getBytes();
    }

    // Cloud providers
    enum CloudProvider {
        GCP(1L, "GCP", "Google Cloud Provider"), AWS(2L, "AWS", "Amazon Web Services"),
        AZURE(3L, "AZURE", "Microsoft Azure"), UNKNOWN(-1L, "UNKNOWN", "Unknown Provider");

        private Long id;
        private String label;
        private String description;

        CloudProvider(Long id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }

        public Long getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public static CloudProvider fromId(Long id) {
            for (CloudProvider e : values()) {
                if (e.id.equals(id))
                    return e;
            }
            return UNKNOWN;
        }

        public static CloudProvider fromLabel(String label) {
            for (CloudProvider e : values()) {
                if (e.label.equals(label))
                    return e;
            }
            return UNKNOWN;
        }
    }
}
