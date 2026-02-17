package org.keycloak.db.compatibility.verifier;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnsupportedMojoTest extends AbstractMojoTest {

    @Test
    void testAddAllChangeSets() throws Exception {
        var classLoader = UnsupportedMojoTest.class.getClassLoader();
        var mojo = new UnsupportedMojo();
        var mapper = new ObjectMapper();

        // Create supported file with a single ChangeSet
        List<ChangeSet> supportedChanges = new ChangeLogXMLParser(classLoader).extractChangeSets("META-INF/jpa-changelog-2.xml");
        assertEquals(1, supportedChanges.size());
        mapper.writeValue(unsupportedFile, supportedChanges);

        // Execute add all and expect all ChangeSets from jpa-changelog-1.xml to be present
        assertTrue(supportedFile.createNewFile());
        mojo.addAllChangeSets(classLoader, supportedFile, unsupportedFile);

        List<ChangeSet> unsupportedChanges = mapper.readValue(supportedFile, new TypeReference<>() {});
        assertEquals(1, unsupportedChanges.size());

        ChangeSet sChange = unsupportedChanges.get(0);
        assertEquals("test", sChange.id());
        assertEquals("keycloak", sChange.author());
        assertEquals("META-INF/jpa-changelog-1.xml", sChange.filename());
    }

    @Test
    void testAddChangeSet() throws Exception {
        var classLoader = UnsupportedMojoTest.class.getClassLoader();
        var changeLogParser = new ChangeLogXMLParser(classLoader);
        var mojo = new UnsupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of()));
        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of()));

        // Test ChangeSet is added to unsupported file as expected
        ChangeSet changeSet = changeLogParser.extractChangeSets("META-INF/jpa-changelog-1.xml").get(0);
        mojo.addChangeSet(classLoader, changeSet, unsupportedFile, supportedFile);

        JsonParent parent = mapper.readValue(unsupportedFile, new TypeReference<>() {});
        List<ChangeSet> unsupportedChanges = new ArrayList<>(parent.changeSets());
        assertEquals(1, unsupportedChanges.size());
        ChangeSet sChange = unsupportedChanges.get(0);
        assertEquals(changeSet.id(), sChange.id());
        assertEquals(changeSet.author(), sChange.author());
        assertEquals(changeSet.filename(), sChange.filename());

        // Test subsequent ChangeSets are added to already populated supported file
        changeSet = changeLogParser.extractChangeSets("META-INF/jpa-changelog-2.xml").get(0);
        mojo.addChangeSet(classLoader, changeSet, unsupportedFile, supportedFile);

        parent = mapper.readValue(unsupportedFile, new TypeReference<>() {});
        unsupportedChanges = new ArrayList<>(parent.changeSets());
        assertEquals(2, unsupportedChanges.size());

        sChange = unsupportedChanges.get(1);
        assertEquals(changeSet.id(), sChange.id());
        assertEquals(changeSet.author(), sChange.author());
        assertEquals(changeSet.filename(), sChange.filename());

        // Test ChangeSet already exists handled gracefully
        mojo.addChangeSet(classLoader, changeSet, unsupportedFile, supportedFile);

        parent = mapper.readValue(unsupportedFile, new TypeReference<>() {});
        unsupportedChanges = new ArrayList<>(parent.changeSets());
        assertEquals(2, unsupportedChanges.size());
    }

    @Test
    void testChangeAlreadySupportedChangeSet() throws Exception {
        var classLoader = UnsupportedMojoTest.class.getClassLoader();
        var mojo = new UnsupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(unsupportedFile, List.of());

        // Create supported file with a single ChangeSet
        List<ChangeSet> unsupportedChanges = new ChangeLogXMLParser(classLoader).extractChangeSets("META-INF/jpa-changelog-1.xml");
        assertEquals(1, unsupportedChanges.size());

        ChangeSet changeSet = unsupportedChanges.get(0);
        mapper.writeValue(supportedFile, new JsonParent(unsupportedChanges, List.of()));

        Exception e = assertThrows(
              MojoExecutionException.class,
              () -> mojo.addChangeSet(classLoader, changeSet, unsupportedFile, supportedFile)
        );

        assertEquals("ChangeSet already defined in the %s file".formatted(supportedFile.getName()), e.getMessage());
    }

    @Test
    void testAddUnknownChangeSet() throws Exception {
        var classLoader = SupportedMojoTest.class.getClassLoader();
        var mojo = new SupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());

        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of()));
        ChangeSet unknown = new ChangeSet("asf", "asfgasg", "afasgfas");

        Exception e = assertThrows(
              MojoExecutionException.class,
              () -> mojo.addChangeSet(classLoader, unknown, unsupportedFile, supportedFile)
        );

        assertEquals("Unknown ChangeSet: " + unknown, e.getMessage());
    }

    @Test
    void testAddMigration() throws Exception {
        var migrationClass = getClass().getName();
        var classLoader = UnsupportedMojoTest.class.getClassLoader();
        var mojo = new UnsupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of()));
        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of()));

        // Test Migration is added to unsupported file as expected
        Migration migration = new Migration(getClass().getName());
        mojo.addMigration(classLoader, migration, unsupportedFile, supportedFile);

        JsonParent parent = mapper.readValue(unsupportedFile, new TypeReference<>() {});
        List<Migration> unsupportedMigrations = new ArrayList<>(parent.migrations());
        assertEquals(1, unsupportedMigrations.size());
        Migration usMigration = unsupportedMigrations.get(0);
        assertEquals(migrationClass, usMigration.clazz());

        // Test subsequent Migration is added to already populated supported file
        migration = new Migration(SupportedMojoTest.class.getName());
        mojo.addMigration(classLoader, migration, unsupportedFile, supportedFile);

        parent = mapper.readValue(unsupportedFile, new TypeReference<>() {});
        unsupportedMigrations = new ArrayList<>(parent.migrations());
        assertEquals(2, unsupportedMigrations.size());

        usMigration = unsupportedMigrations.get(1);
        assertEquals(migration.clazz(), usMigration.clazz());

        // Test existing Migration handled gracefully
        mojo.addMigration(classLoader, migration, unsupportedFile, supportedFile);

        parent = mapper.readValue(unsupportedFile, new TypeReference<>() {});
        unsupportedMigrations = new ArrayList<>(parent.migrations());
        assertEquals(2, unsupportedMigrations.size());
    }

    @Test
    void testMigrationAlreadySupported() throws Exception {
        var migrationClass = getClass().getName();
        var classLoader = UnsupportedMojoTest.class.getClassLoader();
        var mojo = new UnsupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of()));

        // Create supported file with a single Migration
        var migration = new Migration(migrationClass);
        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of(migration)));

        Exception e = assertThrows(
                MojoExecutionException.class,
                () -> mojo.addMigration(classLoader, migration, unsupportedFile, supportedFile)
        );

        assertEquals("Migration already defined in the %s file".formatted(supportedFile.getName()), e.getMessage());
    }

    @Test
    void testAddUnknownMigration() throws Exception {
        var classLoader = getClass().getClassLoader();
        var mojo = new SupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());

        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of()));
        var unknown = new Migration("unknownClass");

        Exception e = assertThrows(
                MojoExecutionException.class,
                () -> mojo.addMigration(classLoader, unknown, unsupportedFile, supportedFile)
        );
        assertEquals("Unknown Migration: " + unknown, e.getMessage());
    }
}
