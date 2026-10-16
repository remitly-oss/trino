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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.security.TrinoPrincipal;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public class AuthorizationRule
{
    private final Optional<StringMatcher> originalUserPattern;
    private final Optional<StringMatcher> originalGroupPattern;
    private final Optional<StringMatcher> originalRolePattern;
    private final Optional<StringMatcher> newUserPattern;
    private final Optional<StringMatcher> newRolePattern;
    private final boolean allow;

    @JsonCreator
    public AuthorizationRule(
            @JsonProperty("original_user") @JsonAlias("originalUser") Optional<StringMatcher> originalUserPattern,
            @JsonProperty("original_group") @JsonAlias("originalGroup") Optional<StringMatcher> originalGroupPattern,
            @JsonProperty("original_role") @JsonAlias("originalRole") Optional<StringMatcher> originalRolePattern,
            @JsonProperty("new_user") @JsonAlias("newUser") Optional<StringMatcher> newUserPattern,
            @JsonProperty("new_role") @JsonAlias("newRole") Optional<StringMatcher> newRolePattern,
            @JsonProperty("allow") Boolean allow)
    {
        checkArgument(newUserPattern.isPresent() || newRolePattern.isPresent(), "At least one of new_use or new_role is required, none were provided");
        this.originalUserPattern = requireNonNull(originalUserPattern, "originalUserPattern is null");
        this.originalGroupPattern = requireNonNull(originalGroupPattern, "originalGroupPattern is null");
        this.originalRolePattern = requireNonNull(originalRolePattern, "originalRolePattern is null");
        this.newUserPattern = requireNonNull(newUserPattern, "newUserPattern is null");
        this.newRolePattern = requireNonNull(newRolePattern, "newRolePattern is null");
        this.allow = requireNonNullElse(allow, TRUE);
    }

    public Optional<Boolean> match(String user, Set<String> groups, Set<String> roles, TrinoPrincipal newPrincipal)
    {
        if (originalUserPattern.map(regex -> regex.matches(user)).orElse(true) &&
                (originalGroupPattern.isEmpty() || originalGroupPattern.get().matchesAny(groups)) &&
                (originalRolePattern.isEmpty() || originalRolePattern.get().matchesAny(roles)) &&
                matches(newPrincipal)) {
            return Optional.of(allow);
        }
        return Optional.empty();
    }

    private boolean matches(TrinoPrincipal newPrincipal)
    {
        return switch (newPrincipal.getType()) {
            case USER -> newUserPattern.map(regex -> regex.matches(newPrincipal.getName())).orElse(false);
            case ROLE -> newRolePattern.map(regex -> regex.matches(newPrincipal.getName())).orElse(false);
        };
    }

    public Optional<StringMatcher> getOriginalRolePattern()
    {
        return originalRolePattern;
    }

    public Optional<StringMatcher> getNewRolePattern()
    {
        return newRolePattern;
    }
}
