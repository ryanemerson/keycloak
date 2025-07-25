package org.keycloak.guides.maven;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FreeMarker {

    private final Map<String, Object> attributes;
    private final Configuration configuration;

    public FreeMarker(File srcDir, Map<String, Object> attributes) throws IOException {
        this.attributes = attributes;

        configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setDirectoryForTemplateLoading(srcDir);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
    }

    public void template(Path template, Path target) throws IOException, TemplateException {
        Template t = configuration.getTemplate(template.toString());
        Path out = target.resolve(template);

        Path parent = out.getParent();
        if (Files.notExists(parent))
            Files.createDirectory(parent);

        HashMap<String, Object> attrs = new HashMap<>(attributes);
        attrs.put("id", id(template));
        attrs.put("attributes", "../".repeat(template.getNameCount() - 1) + "attributes.adoc[]");
        attrs.put("parent", template.getNameCount() > 2 ? template.getName(1).toString() : "");

        try(Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            t.process(attrs, w);
        }
    }

    private String id(Path p) {
        p = p.getNameCount() > 2 ? p.subpath(1, p.getNameCount()) : p.getName(1);
        return Guide.toId(p.toString());
    }
}
