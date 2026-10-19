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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class AnyCatalogSchemaPermissionsRule
{
    private final Optional<StringMatcher> userRegex;
    private final Optional<StringMatcher> groupRegex;
    private final Optional<StringMatcher> roleRegex;
    private final Optional<StringMatcher> catalogRegex;
    private final Optional<StringMatcher> schemaRegex;

    public AnyCatalogSchemaPermissionsRule(Optional<StringMatcher> userRegex, Optional<StringMatcher> roleRegex, Optional<StringMatcher> groupRegex, Optional<StringMatcher> catalogRegex, Optional<StringMatcher> schemaRegex)
    {
        this.userRegex = userRegex;
        this.roleRegex = roleRegex;
        this.groupRegex = groupRegex;
        this.catalogRegex = catalogRegex;
        this.schemaRegex = schemaRegex;
    }

    public boolean match(String user, Set<String> roles, Set<String> groups, String catalogName, String schemaName)
    {
        return userRegex.map(regex -> regex.matches(user)).orElse(true) &&
                roleRegex.map(regex -> regex.matchesAny(roles)).orElse(true) &&
                groupRegex.map(regex -> regex.matchesAny(groups)).orElse(true) &&
                catalogRegex.map(regex -> regex.matches(catalogName)).orElse(true) &&
                schemaRegex.map(regex -> regex.matches(schemaName)).orElse(true);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnyCatalogSchemaPermissionsRule that = (AnyCatalogSchemaPermissionsRule) o;
        return patternEquals(userRegex, that.userRegex) &&
                patternEquals(roleRegex, that.roleRegex) &&
                patternEquals(groupRegex, that.groupRegex) &&
                patternEquals(catalogRegex, that.catalogRegex) &&
                patternEquals(schemaRegex, that.schemaRegex);
    }

    private static boolean patternEquals(Optional<StringMatcher> left, Optional<StringMatcher> right)
    {
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() == right.isEmpty();
        }
        StringMatcher leftPattern = left.get();
        StringMatcher rightPattern = right.get();
        return leftPattern.equals(rightPattern);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(userRegex, roleRegex, groupRegex, catalogRegex, schemaRegex);
    }
}
