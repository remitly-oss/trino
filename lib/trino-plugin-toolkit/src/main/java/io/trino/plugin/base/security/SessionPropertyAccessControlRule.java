/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.base.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class SessionPropertyAccessControlRule
{
    public static final SessionPropertyAccessControlRule ALLOW_ALL = new SessionPropertyAccessControlRule(
            true,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    private final boolean allow;
    private final Optional<StringMatcher> userRegex;
    private final Optional<StringMatcher> roleRegex;
    private final Optional<StringMatcher> groupRegex;
    private final Optional<StringMatcher> propertyRegex;

    @JsonCreator
    public SessionPropertyAccessControlRule(
            @JsonProperty("allow") boolean allow,
            @JsonProperty("user") Optional<StringMatcher> userRegex,
            @JsonProperty("role") Optional<StringMatcher> roleRegex,
            @JsonProperty("group") Optional<StringMatcher> groupRegex,
            @JsonProperty("property") Optional<StringMatcher> propertyRegex)
    {
        this.allow = allow;
        this.userRegex = requireNonNull(userRegex, "userRegex is null");
        this.roleRegex = requireNonNull(roleRegex, "roleRegex is null");
        this.groupRegex = requireNonNull(groupRegex, "groupRegex is null");
        this.propertyRegex = requireNonNull(propertyRegex, "propertyRegex is null");
    }

    boolean isAllow()
    {
        return allow;
    }

    Optional<StringMatcher> getUserRegex()
    {
        return userRegex;
    }

    public Optional<StringMatcher> getRoleRegex()
    {
        return roleRegex;
    }

    Optional<StringMatcher> getGroupRegex()
    {
        return groupRegex;
    }

    public Optional<Boolean> match(String user, Set<String> roles, Set<String> groups, String property)
    {
        if (userRegex.map(regex -> regex.matches(user)).orElse(true) &&
                roleRegex.map(regex -> regex.matchesAny(roles)).orElse(true) &&
                groupRegex.map(regex -> regex.matchesAny(groups)).orElse(true) &&
                propertyRegex.map(regex -> regex.matches(property)).orElse(true)) {
            return Optional.of(allow);
        }
        return Optional.empty();
    }
}
