package com.analysis.tool.impact;

import com.analysis.tool.parser.MethodInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two lists of MethodInfo (representing the same file at two
 * different revisions) and classifies each method as added, removed,
 * modified, or unchanged.
 *
 * Matching strategy (current scope): methods are matched by name only.
 *
 * Limitation: if a file has multiple overloaded methods with the same
 * name but different parameters, this detector cannot currently
 * distinguish between them - they will be treated as one logical
 * method for comparison purposes. Overload-aware matching (using
 * parameter types as part of the identity) is a documented limitation
 * to address in a later phase if needed.
 */
public class MethodChangeDetector {

    public MethodChangeResult detectChanges(List<MethodInfo> oldMethods, List<MethodInfo> newMethods) {
        Map<String, String> oldByName = toBodyMap(oldMethods);
        Map<String, String> newByName = toBodyMap(newMethods);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (String name : newByName.keySet()) {
            if (!oldByName.containsKey(name)) {
                added.add(name);
            } else {
                String oldBody = oldByName.get(name);
                String newBody = newByName.get(name);
                if (oldBody.equals(newBody)) {
                    unchanged.add(name);
                } else {
                    modified.add(name);
                }
            }
        }

        for (String name : oldByName.keySet()) {
            if (!newByName.containsKey(name)) {
                removed.add(name);
            }
        }

        return new MethodChangeResult(added, removed, modified, unchanged);
    }

    private Map<String, String> toBodyMap(List<MethodInfo> methods) {
        Map<String, String> map = new HashMap<>();
        for (MethodInfo method : methods) {
            map.put(method.getName(), method.getBodyText());
        }
        return map;
    }
}
