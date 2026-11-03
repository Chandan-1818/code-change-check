package com.analysis.tool.testgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Joins G2, G3 and G4: for every method TargetDiscoverer supports, builds the
 * receiver expression (ReceiverBuilder) and the argument lists (InputGenerator)
 * and returns them as OracleCases. Order is deterministic.
 */
public class CasePlanner {

    public List<OracleCase> plan(List<String> sources) {
        ReceiverBuilder receivers = new ReceiverBuilder(sources);
        InputGenerator inputs = new InputGenerator();

        List<OracleCase> cases = new ArrayList<>();
        for (DiscoveredMethod method : new TargetDiscoverer().discover(sources)) {
            if (!method.isSupported()) {
                continue;
            }
            String receiver = null;
            if (!method.isStatic()) {
                receiver = receivers.build(method.getClassName()).orElseThrow(() ->
                        new IllegalStateException("Supported method without a constructible receiver: " + method));
            }
            List<String> types = parameterTypes(method.getSignature());
            for (List<String> arguments : inputs.generate(types)) {
                cases.add(new OracleCase(method.getClassName(), method.getMethodName(),
                        method.isStatic(), receiver, types, arguments));
            }
        }
        return cases;
    }

    static List<String> parameterTypes(String signature) {
        String inside = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')')).trim();
        List<String> types = new ArrayList<>();
        if (!inside.isEmpty()) {
            for (String type : inside.split(",")) {
                types.add(type.trim());
            }
        }
        return types;
    }
}
