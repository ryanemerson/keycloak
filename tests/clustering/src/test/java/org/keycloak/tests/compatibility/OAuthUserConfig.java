package org.keycloak.tests.compatibility;

import org.keycloak.testframework.realm.UserConfig;
import org.keycloak.testframework.realm.UserConfigBuilder;

class OAuthUserConfig implements UserConfig {
    @Override
    public UserConfigBuilder configure(UserConfigBuilder user) {
        return user.username("myuser").name("First", "Last")
              .email("test@local")
              .password("password");
    }
}
