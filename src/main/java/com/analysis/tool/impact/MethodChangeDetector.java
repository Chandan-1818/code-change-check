package com.analysis.tool.impact;

import com.analysis.tool.parser.MethodInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two versions of a file's methods and classifies each method NAME
 * as added, removed, modified or unchanged.
 *
 * The rest of the pipeline identifies methods by name, so overloads are
 * grouped under one name. All overloads of a name (declaration + body) are
 * compared together: if ANY overload changes, the name is "modified". This
 * can over-select but can never hide a change, which the earlier
 * last-one-wins map could.
 *
 * Declarations are compared as well as bodies, so a signature-only change
 * (return type, modifiers, throws, annotations) counts as a change.
 */
public class MethodChangeDetector {

    public MethodChangeResult detectChanges(List<MethodInfo> oldMethods, List<MethodInfo> newMethods) {
        Map<String, String> oldByName = toFingerprintMap(oldMethods);
        Map<String, String> newByName = toFingerprintMap(newMethods);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (Map.Entry<String, String> entry : newByName.entrySet()) {
            String name = entry.getKey();
            if (!oldByName.containsKey(name)) {
                added.add(name);
            } else if (oldByName.get(name).equals(entry.getValue())) {
                unchanged.add(name);
            } else {
                modified.add(name);
            }
        }
        for (String name : oldByName.keySet()) {
            if (!newByName.containsKey(name)) {
                removed.add(name);
            }
        }
        return new MethodChangeResult(added, removed, modified, unchanged);
    }

    private Map<String, String> toFingerprintMap(List<MethodInfo> methods) {
        Map<String, List<String>> grouped = new HashMap<>();
        for (MethodInfo method : methods) {
            grouped.computeIfAbsent(method.getName(), k -> new ArrayList<>())
                    .add(method.getDeclaration() + "\n" + method.getBodyText());
        }
        Map<String, String> fingerprints = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> parts = entry.getValue();
            Collections.sort(parts); // order of overloads in the file must not matter
            fingerprints.put(entry.getKey(), String.join("\n----\n", parts));
        }
        return fingerprints;
    }
}
