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

public class SupportedMojoTest extends AbstractMojoTest {

    @Test
    void testAddAllChangeSets() throws Exception {
        var classLoader = SupportedMojoTest.class.getClassLoader();
        var mojo = new SupportedMojo();
        var mapper = new ObjectMapper();

        // Create unsupported file with a single ChangeSet
        List<ChangeSet> unsupportedChanges = new ChangeLogXMLParser(classLoader).extractChangeSets("META-INF/jpa-changelog-2.xml");
        assertEquals(1, unsupportedChanges.size());
        mapper.writeValue(unsupportedFile, unsupportedChanges);

        // Execute add all and expect all ChangeSets from jpa-changelog-1.xml to be present
        assertTrue(supportedFile.createNewFile());
        mojo.addAllChangeSets(classLoader, supportedFile, unsupportedFile);

        List<ChangeSet> supportedChanges = mapper.readValue(supportedFile, new TypeReference<>() {});
        assertEquals(1, supportedChanges.size());

        ChangeSet sChange = supportedChanges.get(0);
        assertEquals("test", sChange.id());
        assertEquals("keycloak", sChange.author());
        assertEquals("META-INF/jpa-changelog-1.xml", sChange.filename());
    }

    @Test
    void testAddChangeSet() throws Exception {
        var classLoader = SupportedMojoTest.class.getClassLoader();
        var changeLogParser = new ChangeLogXMLParser(classLoader);
        var mojo = new SupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of()));
        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of()));

        // Test ChangeSet is added to supported file as expected
        ChangeSet changeSet = changeLogParser.extractChangeSets("META-INF/jpa-changelog-1.xml").get(0);
        mojo.addChangeSet(classLoader, changeSet, supportedFile, unsupportedFile);

        JsonParent parent = mapper.readValue(supportedFile, new TypeReference<>() {});
        List<ChangeSet> supportedChanges = new ArrayList<>(parent.changeSets());
        assertEquals(1, supportedChanges.size());
        ChangeSet sChange = supportedChanges.get(0);
        assertEquals(changeSet.id(), sChange.id());
        assertEquals(changeSet.author(), sChange.author());
        assertEquals(changeSet.filename(), sChange.filename());

        // Test subsequent ChangeSets are added to already populated supported file
        changeSet = changeLogParser.extractChangeSets("META-INF/jpa-changelog-2.xml").get(0);
        mojo.addChangeSet(classLoader, changeSet, supportedFile, unsupportedFile);

        parent = mapper.readValue(supportedFile, new TypeReference<>() {});
        supportedChanges = new ArrayList<>(parent.changeSets());
        assertEquals(2, supportedChanges.size());

        sChange = supportedChanges.get(1);
        assertEquals(changeSet.id(), sChange.id());
        assertEquals(changeSet.author(), sChange.author());
        assertEquals(changeSet.filename(), sChange.filename());

        // Test ChangeSet already exists handled gracefully
        mojo.addChangeSet(classLoader, changeSet, supportedFile, unsupportedFile);

        parent = mapper.readValue(supportedFile, new TypeReference<>() {});
        supportedChanges = new ArrayList<>(parent.changeSets());
        assertEquals(2, supportedChanges.size());
    }

    @Test
    void testChangeAlreadyUnsupportedChangeSet() throws Exception {
        var classLoader = SupportedMojoTest.class.getClassLoader();
        var mojo = new SupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of()));

        // Create unsupported file with a single ChangeSet
        List<ChangeSet> unsupportedChanges = new ChangeLogXMLParser(classLoader).extractChangeSets("META-INF/jpa-changelog-1.xml");
        assertEquals(1, unsupportedChanges.size());

        ChangeSet changeSet = unsupportedChanges.get(0);
        mapper.writeValue(unsupportedFile, new JsonParent(unsupportedChanges, List.of()));

        Exception e = assertThrows(
              MojoExecutionException.class,
              () -> mojo.addChangeSet(classLoader, changeSet, supportedFile, unsupportedFile)
        );

        assertEquals("ChangeSet already defined in the %s file".formatted(unsupportedFile.getName()), e.getMessage());
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
              () -> mojo.addChangeSet(classLoader, unknown, supportedFile, unsupportedFile)
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
        mojo.addMigration(classLoader, migration, supportedFile, unsupportedFile);

        JsonParent parent = mapper.readValue(supportedFile, new TypeReference<>() {});
        List<Migration> supportedMigrations = new ArrayList<>(parent.migrations());
        assertEquals(1, supportedMigrations.size());
        Migration sMigration = supportedMigrations.get(0);
        assertEquals(migrationClass, sMigration.clazz());

        // Test subsequent Migration is added to already populated supported file
        migration = new Migration(UnsupportedMojoTest.class.getName());
        mojo.addMigration(classLoader, migration, supportedFile, unsupportedFile);

        parent = mapper.readValue(supportedFile, new TypeReference<>() {});
        supportedMigrations = new ArrayList<>(parent.migrations());
        assertEquals(2, supportedMigrations.size());

        sMigration = supportedMigrations.get(1);
        assertEquals(migration.clazz(), sMigration.clazz());

        // Test existing Migration handled gracefully
        mojo.addMigration(classLoader, migration, supportedFile, unsupportedFile);

        parent = mapper.readValue(supportedFile, new TypeReference<>() {});
        supportedMigrations = new ArrayList<>(parent.migrations());
        assertEquals(2, supportedMigrations.size());
    }

    @Test
    void testMigrationAlreadyUnsupported() throws Exception {
        var migrationClass = getClass().getName();
        var classLoader = getClass().getClassLoader();
        var mojo = new UnsupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());
        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of()));

        // Create unsupported file with a single Migration
        var migration = new Migration(migrationClass);
        mapper.writeValue(unsupportedFile, new JsonParent(List.of(), List.of(migration)));

        Exception e = assertThrows(
              MojoExecutionException.class,
              () -> mojo.addMigration(classLoader, migration, supportedFile, unsupportedFile)
        );

        assertEquals("Migration already defined in the %s file".formatted(unsupportedFile.getName()), e.getMessage());
    }

    @Test
    void testAddUnknownMigration() throws Exception {
        var classLoader = getClass().getClassLoader();
        var mojo = new SupportedMojo();
        var mapper = new ObjectMapper();

        assertTrue(supportedFile.createNewFile());
        assertTrue(unsupportedFile.createNewFile());

        mapper.writeValue(supportedFile, new JsonParent(List.of(), List.of()));
        var unknown = new Migration("unknownClass");

        Exception e = assertThrows(
              MojoExecutionException.class,
              () -> mojo.addMigration(classLoader, unknown, supportedFile, unsupportedFile)
        );
        assertEquals("Unknown Migration: " + unknown, e.getMessage());
    }
}
