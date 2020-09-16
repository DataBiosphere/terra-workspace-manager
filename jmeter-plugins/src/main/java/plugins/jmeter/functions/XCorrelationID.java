package plugins.jmeter.functions;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;
import org.hashids.Hashids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XCorrelationID extends AbstractFunction {
    private Hashids hashids = new Hashids("XCorrelationIDSalt", 8);
    private static final Logger log = LoggerFactory.getLogger(XCorrelationID.class);
    private static final List<String> desc = new LinkedList<>();

    private static final String KEY = "__XCorrelationID"; //$NON-NLS-1$

    // Number of parameters expected - used to reject invalid calls
    private static final int MIN_PARAMETER_COUNT = 1;
    private static final int MAX_PARAMETER_COUNT = 2;

    static {
        desc.add(getResString("x_correlation_id_prefix")); //$NON-NLS-1$
        desc.add(getResString("function_name_paropt")); //$NON-NLS-1$
    }

    private Object[] values;

    public XCorrelationID() {

    }

    @Override
    public List<String> getArgumentDesc() {
        return desc;
    }

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) throws InvalidVariableException {
        StringBuilder uniqueId = new StringBuilder();
        String correlationId = generateCorrelationId();
        String prefix = ((CompoundVariable) values[0]).execute().trim();
        if (prefix.length() > 0) {
            uniqueId.append(prefix).append("-");
        }
        uniqueId.append(correlationId);

        JMeterVariables vars = getVariables();

        String varName = null;

        if (values.length > 1)
            varName = ((CompoundVariable) values[1]).execute().trim();

        if (vars != null && varName != null) {
            vars.put(varName, uniqueId.toString());
        }

        return uniqueId.toString();
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

    private String generateCorrelationId() {
        long generatedLong = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);

        return hashids.encode(generatedLong);
    }
}
