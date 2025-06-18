package org.keycloak.testframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectHttpClient {
    boolean followRedirects() default true;

    // TODO add javadocs
    Class<? extends CookieStore> cookieStore() default BasicCookieStore.class;
}
