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
import io.trino.plugin.base.security.ProcedureAccessControlRule.ProcedurePrivilege;
import io.trino.spi.connector.CatalogSchemaRoutineName;

import java.util.Optional;
import java.util.Set;

import static io.trino.plugin.base.security.ProcedureAccessControlRule.ProcedurePrivilege.EXECUTE;
import static io.trino.plugin.base.security.ProcedureAccessControlRule.ProcedurePrivilege.GRANT_EXECUTE;
import static java.util.Objects.requireNonNull;

public class CatalogProcedureAccessControlRule
{
    public static final CatalogProcedureAccessControlRule ALLOW_BUILTIN = new CatalogProcedureAccessControlRule(
            ImmutableSet.of(EXECUTE, GRANT_EXECUTE),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(StringMatcher.of("system")),
            Optional.of(StringMatcher.of("builtin")),
            Optional.empty());

    private final Optional<StringMatcher> catalogRegex;
    private final ProcedureAccessControlRule procedureAccessControlRule;

    @JsonCreator
    public CatalogProcedureAccessControlRule(
            @JsonProperty("privileges") Set<ProcedurePrivilege> privileges,
            @JsonProperty("user") Optional<StringMatcher> userRegex,
            @JsonProperty("role") Optional<StringMatcher> roleRegex,
            @JsonProperty("group") Optional<StringMatcher> groupRegex,
            @JsonProperty("catalog") Optional<StringMatcher> catalogRegex,
            @JsonProperty("schema") Optional<StringMatcher> schemaRegex,
            @JsonProperty("procedure") Optional<StringMatcher> procedureRegex)
    {
        this(catalogRegex, new ProcedureAccessControlRule(privileges, userRegex, roleRegex, groupRegex, schemaRegex, procedureRegex));
    }

    private CatalogProcedureAccessControlRule(Optional<StringMatcher> catalogRegex, ProcedureAccessControlRule procedureAccessControlRule)
    {
        this.catalogRegex = requireNonNull(catalogRegex, "catalogRegex is null");
        this.procedureAccessControlRule = requireNonNull(procedureAccessControlRule, "procedureAccessControlRule is null");
    }

    public boolean matches(String user, Set<String> roles, Set<String> groups, CatalogSchemaRoutineName procedureName)
    {
        if (!catalogRegex.map(regex -> regex.matches(procedureName.getCatalogName())).orElse(true)) {
            return false;
        }
        return procedureAccessControlRule.matches(user, roles, groups, procedureName.getSchemaRoutineName());
    }

    Optional<AnyCatalogPermissionsRule> toAnyCatalogPermissionsRule()
    {
        if (procedureAccessControlRule.getPrivileges().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AnyCatalogPermissionsRule(
                procedureAccessControlRule.getUserRegex(),
                procedureAccessControlRule.getRoleRegex(),
                procedureAccessControlRule.getGroupRegex(),
                catalogRegex));
    }

    Optional<AnyCatalogSchemaPermissionsRule> toAnyCatalogSchemaPermissionsRule()
    {
        if (procedureAccessControlRule.getPrivileges().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AnyCatalogSchemaPermissionsRule(
                procedureAccessControlRule.getUserRegex(),
                procedureAccessControlRule.getRoleRegex(),
                procedureAccessControlRule.getGroupRegex(),
                catalogRegex,
                procedureAccessControlRule.getSchemaRegex()));
    }

    public boolean canExecuteProcedure()
    {
        return procedureAccessControlRule.canExecuteProcedure();
    }

    public boolean canGrantExecuteProcedure()
    {
        return procedureAccessControlRule.canGrantExecuteProcedure();
    }
}
