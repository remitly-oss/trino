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

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class IdentityMatcher
{
    private final Optional<StringMatcher> userRegex;
    private final Optional<StringMatcher> roleRegex;
    private final Optional<StringMatcher> groupRegex;

    public IdentityMatcher(Optional<StringMatcher> userRegex, Optional<StringMatcher> roleRegex, Optional<StringMatcher> groupRegex)
    {
        this.userRegex = requireNonNull(userRegex, "userRegex is null");
        this.roleRegex = requireNonNull(roleRegex, "roleRegex is null");
        this.groupRegex = requireNonNull(groupRegex, "groupRegex is null");
    }

    public boolean matches(String user, Set<String> roles, Set<String> groups)
    {
        return userRegex.map(regex -> regex.matches(user)).orElse(true) &&
                roleRegex.map(regex -> regex.matchesAny(roles)).orElse(true) &&
                groupRegex.map(regex -> regex.matchesAny(groups)).orElse(true);
    }

    public Optional<StringMatcher> getRoleRegex()
    {
        return roleRegex;
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
        IdentityMatcher that = (IdentityMatcher) o;
        return Objects.equals(userRegex, that.userRegex) &&
                Objects.equals(roleRegex, that.roleRegex) &&
                Objects.equals(groupRegex, that.groupRegex);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(userRegex, roleRegex, groupRegex);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .omitNullValues()
                .add("userRegex", userRegex.orElse(null))
                .add("roleRegex", roleRegex.orElse(null))
                .add("groupRegex", groupRegex.orElse(null))
                .toString();
    }
}
