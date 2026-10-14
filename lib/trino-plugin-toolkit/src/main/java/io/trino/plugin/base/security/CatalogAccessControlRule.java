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
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.trino.plugin.base.security.CatalogAccessControlRule.AccessMode.ALL;
import static java.util.Objects.requireNonNull;

public class CatalogAccessControlRule
{
    public static final CatalogAccessControlRule ALLOW_ALL = new CatalogAccessControlRule(
            ALL,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    private final AccessMode accessMode;
    private final Optional<StringMatcher> userRegex;
    private final Optional<StringMatcher> roleRegex;
    private final Optional<StringMatcher> groupRegex;
    private final Optional<StringMatcher> catalogRegex;

    @JsonCreator
    public CatalogAccessControlRule(
            @JsonProperty("allow") AccessMode accessMode,
            @JsonProperty("user") Optional<StringMatcher> userRegex,
            @JsonProperty("role") Optional<StringMatcher> roleRegex,
            @JsonProperty("group") Optional<StringMatcher> groupRegex,
            @JsonProperty("catalog") Optional<StringMatcher> catalogRegex)
    {
        this.accessMode = requireNonNull(accessMode, "accessMode is null");
        this.userRegex = requireNonNull(userRegex, "userRegex is null");
        this.roleRegex = requireNonNull(roleRegex, "roleRegex is null");
        this.groupRegex = requireNonNull(groupRegex, "groupRegex is null");
        this.catalogRegex = requireNonNull(catalogRegex, "catalogRegex is null");
    }

    public Optional<AccessMode> match(String user, Set<String> roles, Set<String> groups, String catalog)
    {
        if (userRegex.map(regex -> regex.matches(user)).orElse(true) &&
                roleRegex.map(regex -> regex.matchesAny(roles)).orElse(true) &&
                groupRegex.map(regex -> regex.matchesAny(groups)).orElse(true) &&
                catalogRegex.map(regex -> regex.matches(catalog)).orElse(true)) {
            return Optional.of(accessMode);
        }
        return Optional.empty();
    }

    public enum AccessMode
    {
        OWNER("owner"),
        ALL("all"),
        READ_ONLY("read-only"),
        NONE("none");

        private static final Map<String, AccessMode> modeByName = Maps.uniqueIndex(ImmutableList.copyOf(AccessMode.values()), AccessMode::toString);

        private final String stringValue;

        AccessMode(String stringValue)
        {
            this.stringValue = requireNonNull(stringValue, "stringValue is null");
        }

        @JsonValue
        @Override
        public String toString()
        {
            return stringValue;
        }

        @JsonCreator
        public static AccessMode fromJson(Object value)
        {
            if (Boolean.TRUE.equals(value)) {
                return ALL;
            }
            if (Boolean.FALSE.equals(value)) {
                return NONE;
            }
            if (value instanceof String string) {
                AccessMode accessMode = modeByName.get(string.toLowerCase(Locale.US));
                if (accessMode != null) {
                    return accessMode;
                }
            }

            throw new IllegalArgumentException("Unknown " + AccessMode.class.getSimpleName() + ": " + value);
        }

        boolean implies(AccessMode other)
        {
            if (this == OWNER && (other == ALL || other == READ_ONLY)) {
                return true;
            }
            if (this == ALL && other == READ_ONLY) {
                return true;
            }
            return this == other;
        }
    }
}
