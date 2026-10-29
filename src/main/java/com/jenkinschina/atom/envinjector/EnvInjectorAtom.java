package com.jenkinschina.atom.envinjector;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * envinject as an atom: read a properties file and hand the values to the steps that follow.
 *
 * <p>There is no env API to call: an output whose key is a legal variable name <em>becomes</em> that variable, so the
 * atom only has to return <code>APP_ENV=staging</code> and the next step sees <code>$APP_ENV</code>. Keys that are not
 * legal variable names are reported and skipped rather than exported as something surprising.
 */
public class EnvInjectorAtom implements AtomTask {

    private static final Pattern LEGAL = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        Path workspace = context.getWorkspace();
        String files = context.getParam("files", ".env");
        String prefix = context.getParam("prefix", "");
        boolean overwrite = context.getBooleanParam("overwrite", true);
        boolean failOnMissing = context.getBooleanParam("failOnMissing", true);

        Map<String, String> values = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int read = 0;

        for (String spec : files.split(",")) {
            String trimmed = spec.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path file = workspace.resolve(trimmed);
            if (!Files.isRegularFile(file)) {
                missing.add(trimmed);
                continue;
            }
            read++;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith("#") || entry.startsWith("!")) {
                    continue;
                }
                int equals = entry.indexOf('=');
                if (equals <= 0) {
                    skipped.add(entry);
                    continue;
                }
                String key = entry.substring(0, equals).strip();
                String value = unquote(entry.substring(equals + 1).strip());
                if (!LEGAL.matcher(key).matches()) {
                    skipped.add(key);
                    continue;
                }
                String exported = prefix + key;
                if (!LEGAL.matcher(exported).matches()) {
                    skipped.add(exported);
                    continue;
                }
                // the file wins over the parameter of the same name only when overwrite is on
                if (overwrite || context.getParam(exported) == null) {
                    values.put(exported, value);
                }
            }
        }

        if (!missing.isEmpty() && failOnMissing) {
            return AtomResult.error(ErrorType.USER, 40001, "no such file in the workspace: "
                    + String.join(", ", missing));
        }
        if (values.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40002, read == 0
                    ? "no file matched " + files
                    : "no KEY=VALUE line in " + files);
        }

        AtomResult result = AtomResult.success()
                .data("count", String.valueOf(values.size()))
                .data("keys", String.join(",", values.keySet()));
        values.forEach(result::data);
        // a value that looks like a credential is not echoed
        for (Map.Entry<String, String> entry : values.entrySet()) {
            boolean secretish = entry.getKey().matches("(?i).*(TOKEN|SECRET|PASSWORD|PASSWD|KEY|CREDENTIAL).*");
            context.log("[env-injector] " + entry.getKey() + "="
                    + (secretish ? "***" : abbreviate(entry.getValue())));
        }
        if (!skipped.isEmpty()) {
            context.log("[env-injector] skipped " + skipped.size() + " unusable key(s): "
                    + String.join(", ", skipped.subList(0, Math.min(10, skipped.size()))));
        }
        context.log("[env-injector] exported " + values.size() + " variable(s) from " + read + " file(s)");
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String abbreviate(String value) {
        return value.length() > 60 ? value.substring(0, 60) + "…" : value;
    }
}
