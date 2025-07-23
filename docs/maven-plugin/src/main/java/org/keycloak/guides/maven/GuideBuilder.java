package org.keycloak.guides.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;
import org.keycloak.common.Version;

import freemarker.template.TemplateException;

public class GuideBuilder {

    private final FreeMarker freeMarker;
    private final File srcDir;
    private final File targetDir;
    private final Log log;

    // TODO convert to use Path
    public GuideBuilder(File srcDir, File targetDir, Log log, Properties properties) throws IOException {
        this.srcDir = srcDir;
        this.targetDir = targetDir;
        this.log = log;

        Map<String, Object> globalAttributes = new HashMap<>();
        globalAttributes.put("ctx", new Context(srcDir));
        globalAttributes.put("version", Version.VERSION);
        globalAttributes.put("properties", properties);

        this.freeMarker = new FreeMarker(srcDir.getParentFile(), globalAttributes);
    }

    public void build() throws TemplateException, IOException {
        if (!srcDir.isDirectory()) {
            if (!srcDir.mkdir()) {
                throw new RuntimeException("Can't create folder " + srcDir);
            }
        }

        Path srcPath = srcDir.toPath();
        Path partials = srcPath.resolve("partials");
        List<Path> templatePaths;
        try (Stream<Path> files = Files.walk(srcDir.toPath())) {
            templatePaths = files
                  .filter(Files::isRegularFile)
                  .filter(p -> !p.startsWith(partials))
                  .filter(p -> p.getFileName().toString().endsWith(".adoc"))
                  .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover templates in " + srcDir, e);
        }

        for (Path path : templatePaths) {
            Path relativePath = srcDir.toPath().getParent().relativize(path);
            freeMarker.template(relativePath, targetDir.getParentFile().toPath());
            if (log != null) {
                log.info("Templated: " + relativePath);
            }
        }
    }
}
