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
import com.google.common.collect.ImmutableSet;
import io.trino.spi.connector.SchemaRoutineName;

import java.util.Optional;
import java.util.Set;

import static io.trino.plugin.base.security.ProcedureAccessControlRule.ProcedurePrivilege.EXECUTE;
import static io.trino.plugin.base.security.ProcedureAccessControlRule.ProcedurePrivilege.GRANT_EXECUTE;
import static java.util.Objects.requireNonNull;

public class ProcedureAccessControlRule
{
    private final Set<ProcedurePrivilege> privileges;
    private final Optional<StringMatcher> userRegex;
    private final Optional<StringMatcher> roleRegex;
    private final Optional<StringMatcher> groupRegex;
    private final Optional<StringMatcher> schemaRegex;
    private final Optional<StringMatcher> procedureRegex;

    @JsonCreator
    public ProcedureAccessControlRule(
            @JsonProperty("privileges") Set<ProcedurePrivilege> privileges,
            @JsonProperty("user") Optional<StringMatcher> userRegex,
            @JsonProperty("role") Optional<StringMatcher> roleRegex,
            @JsonProperty("group") Optional<StringMatcher> groupRegex,
            @JsonProperty("schema") Optional<StringMatcher> schemaRegex,
            @JsonProperty("procedure") Optional<StringMatcher> procedureRegex)
    {
        this.privileges = ImmutableSet.copyOf(requireNonNull(privileges, "privileges is null"));
        this.userRegex = requireNonNull(userRegex, "userRegex is null");
        this.roleRegex = requireNonNull(roleRegex, "roleRegex is null");
        this.groupRegex = requireNonNull(groupRegex, "groupRegex is null");
        this.schemaRegex = requireNonNull(schemaRegex, "schemaRegex is null");
        this.procedureRegex = requireNonNull(procedureRegex, "procedureRegex is null");
    }

    public boolean matches(String user, Set<String> roles, Set<String> groups, SchemaRoutineName procedureName)
    {
        return userRegex.map(regex -> regex.matches(user)).orElse(true) &&
                roleRegex.map(regex -> regex.matchesAny(roles)).orElse(true) &&
                groupRegex.map(regex -> regex.matchesAny(groups)).orElse(true) &&
                schemaRegex.map(regex -> regex.matches(procedureName.getSchemaName())).orElse(true) &&
                procedureRegex.map(regex -> regex.matches(procedureName.getRoutineName())).orElse(true);
    }

    public boolean canExecuteProcedure()
    {
        return privileges.contains(EXECUTE) || canGrantExecuteProcedure();
    }

    public boolean canGrantExecuteProcedure()
    {
        return privileges.contains(GRANT_EXECUTE);
    }

    Optional<AnySchemaPermissionsRule> toAnySchemaPermissionsRule()
    {
        if (privileges.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AnySchemaPermissionsRule(userRegex, roleRegex, groupRegex, schemaRegex));
    }

    Set<ProcedurePrivilege> getPrivileges()
    {
        return privileges;
    }

    Optional<StringMatcher> getUserRegex()
    {
        return userRegex;
    }

    Optional<StringMatcher> getRoleRegex()
    {
        return roleRegex;
    }

    Optional<StringMatcher> getGroupRegex()
    {
        return groupRegex;
    }

    Optional<StringMatcher> getSchemaRegex()
    {
        return schemaRegex;
    }

    public enum ProcedurePrivilege
    {
        EXECUTE, GRANT_EXECUTE
    }
}
