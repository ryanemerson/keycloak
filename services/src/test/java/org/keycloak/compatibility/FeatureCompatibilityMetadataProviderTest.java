package org.keycloak.compatibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.keycloak.common.Profile;
import org.keycloak.common.profile.ProfileConfigResolver;

public class FeatureCompatibilityMetadataProviderTest extends AbstractCompatibilityMetadataProviderTest {

    @Test
    public void testRollingPolicy() {
        FeatureCompatibilityMetadataProvider provider = new FeatureCompatibilityMetadataProvider();
        Profile.configure();

        var f = FeatureCompatibilityMetadataProvider.Feature.from(Profile.Feature.PASSKEYS);
        assertCompatibility(CompatibilityResult.ExitCode.ROLLING, provider.isCompatible(Map.of(f.key(), FeatureCompatibilityMetadataProvider.toJson(f))));

        Profile.configure(new FeatureResolver(Profile.Feature.PASSKEYS, false));
        f = FeatureCompatibilityMetadataProvider.Feature.from(Profile.Feature.PASSKEYS);
        var featureDisabledMap = Map.of(f.key(), FeatureCompatibilityMetadataProvider.toJson(f));
        Profile.reset();
        Profile.configure();
        assertCompatibility(CompatibilityResult.ExitCode.ROLLING, provider.isCompatible(featureDisabledMap));
    }

    @Test
    public void testRollingNoUpgradePolicy() {
        FeatureCompatibilityMetadataProvider provider = new FeatureCompatibilityMetadataProvider();
        Profile.configure();

        var f = FeatureCompatibilityMetadataProvider.Feature.from(Profile.Feature.ROLLING_UPDATES_V1);
        assertEquals(Profile.FeatureUpdatePolicy.ROLLING_NO_UPGRADE, f.updatePolicy());
        assertEquals(1, f.version());
        assertTrue(f.enabled());
        var featureV1Map = Map.of(f.key(), FeatureCompatibilityMetadataProvider.toJson(f));

        Profile.reset();
        Profile.configure();
        assertCompatibility(CompatibilityResult.ExitCode.RECREATE, provider.isCompatible(featureV1Map));
    }

    @Test
    public void testFeatureShutdownPolicy() {
        FeatureCompatibilityMetadataProvider provider = new FeatureCompatibilityMetadataProvider();

        // Test both old and new have enabled feature resulting in Rolling result
        Profile.configure();
        var f = FeatureCompatibilityMetadataProvider.Feature.from(Profile.Feature.PERSISTENT_USER_SESSIONS);
        var featureEnabledMap = Map.of(f.key(), FeatureCompatibilityMetadataProvider.toJson(f));
        assertEquals(Profile.FeatureUpdatePolicy.SHUTDOWN, f.updatePolicy());
        assertCompatibility(CompatibilityResult.ExitCode.ROLLING, provider.isCompatible(featureEnabledMap));

        // Test new metadata has feature enabled and old metadata has feature disabled results in Shutdown
        Profile.configure(new FeatureResolver(Profile.Feature.PERSISTENT_USER_SESSIONS, false));
        f = FeatureCompatibilityMetadataProvider.Feature.from(Profile.Feature.PERSISTENT_USER_SESSIONS);
        var featureDisabledMap = Map.of(f.key(), FeatureCompatibilityMetadataProvider.toJson(f));
        Profile.reset();
        Profile.configure();
        assertCompatibility(CompatibilityResult.ExitCode.RECREATE, provider.isCompatible(featureDisabledMap));

        // Test old metadata has feature enabled and new metadata has feature disabled results in Shutdown
        Profile.reset();
        Profile.configure(new FeatureResolver(Profile.Feature.PERSISTENT_USER_SESSIONS, false));
        assertCompatibility(CompatibilityResult.ExitCode.RECREATE, provider.isCompatible(featureEnabledMap));
    }

    @Test
    public void testRemovedFeatureCausesShutdown() {
        FeatureCompatibilityMetadataProvider provider = new FeatureCompatibilityMetadataProvider();
        Profile.configure();
        var featureJson = "{\"key\":\"deleted-feature\",\"enabled\":true,\"version\":1,\"updatePolicy\":\"SHUTDOWN\"}";
        assertCompatibility(CompatibilityResult.ExitCode.RECREATE, provider.isCompatible(Map.of("deleted-feature", featureJson)));
    }

    record FeatureResolver(Profile.Feature feature, boolean enabled) implements ProfileConfigResolver {
        @Override
        public Profile.ProfileName getProfileName() {
            return null;
        }

        @Override
        public FeatureConfig getFeatureConfig(String featureName) {
            return feature.getUnversionedKey().equals(featureName) ? enabled ? FeatureConfig.ENABLED : FeatureConfig.DISABLED : FeatureConfig.UNCONFIGURED;
        }
    }
}
