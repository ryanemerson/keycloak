package org.keycloak.db.compatibility.verifier;

import java.util.Collection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateSnapshotMojoTest extends AbstractMojoTest {

    @Test
    void testSnapshotFilesCreated() throws Exception {
        var classLoader = CreateSnapshotMojoTest.class.getClassLoader();
        var mojo = new CreateSnapshotMojo();
        mojo.createSnapshot(classLoader, supportedFile, unsupportedFile, "org.keycloak.db.compatibility.verifier.test");

        assertTrue(supportedFile.exists());
        assertTrue(unsupportedFile.exists());

        var mapper = new ObjectMapper();
        JsonParent json = mapper.readValue(supportedFile, new TypeReference<>() {});;
        assertEquals(2, json.changeSets().size());
        assertEquals(1, json.migrations().size());

        json = mapper.readValue(unsupportedFile, new TypeReference<>() {});;
        assertEquals(0, json.changeSets().size());
        assertEquals(0, json.migrations().size());
    }
}
