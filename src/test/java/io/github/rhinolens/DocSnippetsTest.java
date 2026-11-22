package io.github.rhinolens;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Keeps the documentation true:
 * <ul>
 * <li>every {@code <!-- snippet: name -->} code block in README.md and docs/ equals the
 *     {@code // snippet: name} block in the examples (compiled and run by ExamplesTest);</li>
 * <li>every {@code <!-- include: path -->} block equals that file (e.g. the generated report example);</li>
 * <li>every public type has a page in docs/api/;</li>
 * <li>every relative link points to an existing file.</li>
 * </ul>
 * Run with {@code -Dlens.updateDocs=true} to rewrite the doc blocks from their sources.
 */
@Order(1000)
class DocSnippetsTest {

    private static final Path ROOT = Paths.get("");
    private static final Pattern SOURCE_SNIPPET =
            Pattern.compile("// snippet: ([\\w-]+)\\R(.*?)\\R[ \\t]*// end-snippet", Pattern.DOTALL);
    private static final Pattern DOC_BLOCK = Pattern.compile(
            "<!-- (snippet|include): ([^ ]+) -->\\R```(\\w*)\\R(.*?)\\R?```", Pattern.DOTALL);
    private static final Pattern JAVA_STRING_LINE = Pattern.compile("^\"(.*)\",?(\\s*//.*)?$");
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern PUBLIC_TYPE =
            Pattern.compile("^public (?:final |abstract )?(?:class|interface|enum) (\\w+)", Pattern.MULTILINE);

    static boolean updating() {
        return Boolean.parseBoolean(System.getProperty("lens.updateDocs", "false"));
    }

    @Test
    void docBlocksMatchTheirSources() throws IOException {
        Map<String, String> snippets = sourceSnippets();
        List<String> problems = new ArrayList<>();
        for (Path doc : docs()) {
            String text = Files.readString(doc);
            Matcher m = DOC_BLOCK.matcher(text);
            StringBuffer rewritten = new StringBuffer();
            while (m.find()) {
                String kind = m.group(1);
                String name = m.group(2);
                String expected;
                if (kind.equals("snippet")) {
                    expected = snippets.get(name);
                    if (expected == null) {
                        problems.add(doc + ": unknown snippet '" + name + "'");
                        expected = m.group(4);
                    }
                } else {
                    Path file = ROOT.resolve(name);
                    if (!Files.exists(file)) {
                        problems.add(doc + ": include of missing file " + name);
                        expected = m.group(4);
                    } else {
                        expected = Files.readString(file).stripTrailing();
                    }
                }
                if (!expected.equals(m.group(4))) {
                    problems.add(doc + ": block '" + name + "' differs from its source");
                }
                String replacement = "<!-- " + kind + ": " + name + " -->\n```" + m.group(3) + "\n" + expected + "\n```";
                m.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(rewritten);
            if (updating() && !rewritten.toString().equals(text)) {
                Files.writeString(doc, rewritten.toString());
            }
        }
        if (!updating()) {
            assertThat(problems).as("run with -Dlens.updateDocs=true to refresh docs").isEmpty();
        }
        assertThat(problems.stream().filter(p -> p.contains("unknown") || p.contains("missing"))).isEmpty();
    }

    @Test
    void everyPublicTypeHasAnApiPage() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(Paths.get("src/main/java/io/github/rhinolens"))) {
            for (Path f : files.collect(Collectors.toList())) {
                Matcher m = PUBLIC_TYPE.matcher(Files.readString(f));
                if (m.find() && !Files.exists(Paths.get("docs/api/" + m.group(1) + ".md"))) {
                    missing.add(m.group(1));
                }
            }
        }
        assertThat(missing).as("public types without docs/api/<Type>.md").isEmpty();
    }

    @Test
    void relativeLinksResolve() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path doc : docs()) {
            Matcher m = LINK.matcher(Files.readString(doc));
            while (m.find()) {
                String target = m.group(1);
                if (target.startsWith("http") || target.startsWith("#") || target.startsWith("mailto:")) {
                    continue;
                }
                String path = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
                Path resolved = doc.toAbsolutePath().getParent().resolve(path).normalize();
                if (!Files.exists(resolved)) {
                    broken.add(doc + " -> " + target);
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    private static List<Path> docs() throws IOException {
        List<Path> out = new ArrayList<>();
        out.add(Paths.get("README.md"));
        try (Stream<Path> files = Files.walk(Paths.get("docs"))) {
            files.filter(p -> p.toString().endsWith(".md")).sorted().forEach(out::add);
        }
        return out;
    }

    static Map<String, String> sourceSnippets() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/test/java/io/github/rhinolens/examples"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                Matcher m = SOURCE_SNIPPET.matcher(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                while (m.find()) {
                    out.put(m.group(1), clean(m.group(2)));
                }
            }
        }
        return out;
    }

    /** Removes common indentation; a block of Java string literals becomes the script it spells out. */
    private static String clean(String block) {
        List<String> lines = new ArrayList<>(List.of(block.split("\\R", -1)));
        int indent = Integer.MAX_VALUE;
        for (String l : lines) {
            if (!l.isBlank()) {
                indent = Math.min(indent, l.length() - l.stripLeading().length());
            }
        }
        List<String> out = new ArrayList<>();
        boolean allStrings = true;
        for (String l : lines) {
            String d = l.isBlank() ? "" : l.substring(indent);
            out.add(d);
            if (!d.isBlank() && !JAVA_STRING_LINE.matcher(d.strip()).matches()) {
                allStrings = false;
            }
        }
        if (allStrings) {
            List<String> script = new ArrayList<>();
            for (String d : out) {
                Matcher m = JAVA_STRING_LINE.matcher(d.strip());
                if (m.matches()) {
                    script.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
                }
            }
            return String.join("\n", script);
        }
        return String.join("\n", out);
    }
}
