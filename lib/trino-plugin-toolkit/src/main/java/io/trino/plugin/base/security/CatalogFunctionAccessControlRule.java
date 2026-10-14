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
import com.google.common.collect.ImmutableSet;
import io.trino.plugin.base.security.FunctionAccessControlRule.FunctionPrivilege;
import io.trino.spi.connector.CatalogSchemaRoutineName;
import io.trino.spi.function.FunctionKind;

import java.util.Optional;
import java.util.Set;

import static io.trino.plugin.base.security.FunctionAccessControlRule.FunctionPrivilege.EXECUTE;
import static io.trino.plugin.base.security.FunctionAccessControlRule.FunctionPrivilege.GRANT_EXECUTE;
import static java.util.Objects.requireNonNull;

public class CatalogFunctionAccessControlRule
{
    public static final CatalogFunctionAccessControlRule ALLOW_BUILTIN = new CatalogFunctionAccessControlRule(
            ImmutableSet.of(EXECUTE, GRANT_EXECUTE),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(StringMatcher.of("system")),
            Optional.of(StringMatcher.of("builtin")),
            Optional.empty(),
            ImmutableSet.of());

    private final Optional<StringMatcher> catalogRegex;
    private final FunctionAccessControlRule functionAccessControlRule;

    @JsonCreator
    public CatalogFunctionAccessControlRule(
            @JsonProperty("privileges") Set<FunctionPrivilege> privileges,
            @JsonProperty("user") Optional<StringMatcher> userRegex,
            @JsonProperty("role") Optional<StringMatcher> roleRegex,
            @JsonProperty("group") Optional<StringMatcher> groupRegex,
            @JsonProperty("catalog") Optional<StringMatcher> catalogRegex,
            @JsonProperty("schema") Optional<StringMatcher> schemaRegex,
            @JsonProperty("function") Optional<StringMatcher> tableFunctionRegex,
            @JsonProperty("function_kinds") @JsonAlias("functionKinds") Set<FunctionKind> functionKinds)
    {
        this(catalogRegex, new FunctionAccessControlRule(privileges, userRegex, roleRegex, groupRegex, schemaRegex, tableFunctionRegex, functionKinds));
    }

    private CatalogFunctionAccessControlRule(Optional<StringMatcher> catalogRegex, FunctionAccessControlRule functionAccessControlRule)
    {
        this.catalogRegex = requireNonNull(catalogRegex, "catalogRegex is null");
        this.functionAccessControlRule = requireNonNull(functionAccessControlRule, "functionAccessControlRule is null");
    }

    public boolean matches(String user, Set<String> roles, Set<String> groups, CatalogSchemaRoutineName functionName)
    {
        if (!catalogRegex.map(regex -> regex.matches(functionName.getCatalogName())).orElse(true)) {
            return false;
        }
        return functionAccessControlRule.matches(user, roles, groups, functionName.getSchemaRoutineName());
    }

    Optional<AnyCatalogPermissionsRule> toAnyCatalogPermissionsRule()
    {
        if (functionAccessControlRule.getPrivileges().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AnyCatalogPermissionsRule(
                functionAccessControlRule.getUserRegex(),
                functionAccessControlRule.getRoleRegex(),
                functionAccessControlRule.getGroupRegex(),
                catalogRegex));
    }

    Optional<AnyCatalogSchemaPermissionsRule> toAnyCatalogSchemaPermissionsRule()
    {
        if (functionAccessControlRule.getPrivileges().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AnyCatalogSchemaPermissionsRule(
                functionAccessControlRule.getUserRegex(),
                functionAccessControlRule.getRoleRegex(),
                functionAccessControlRule.getGroupRegex(),
                catalogRegex,
                functionAccessControlRule.getSchemaRegex()));
    }

    public boolean canExecuteFunction()
    {
        return functionAccessControlRule.canExecuteFunction();
    }

    public boolean canGrantExecuteFunction()
    {
        return functionAccessControlRule.canGrantExecuteFunction();
    }

    public boolean hasOwnership()
    {
        return functionAccessControlRule.hasOwnership();
    }
}
