package org.spruce.api.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the default method name used when calling a service through the gateway.
 * <p>
 * By default, the method name in the interface is used as the gateway action name.
 * This annotation allows you to explicitly specify a different action name.
 * <p>
 * Example:
 * <p>
 * {@code
 * @ServiceCall("fetchFriends")
 * CompletableFuture<GetFriendsResponse> getFriends(GetFriendsRequest request);
 * }
 *
 * In this case, the gateway will call action "fetchFriends" instead of "getFriends".
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceCall {
    String value();
}
