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
import io.trino.spi.connector.CatalogSchemaName;

import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class CatalogSchemaAccessControlRule
{
    public static final CatalogSchemaAccessControlRule ALLOW_ALL = new CatalogSchemaAccessControlRule(SchemaAccessControlRule.ALLOW_ALL, Optional.empty());

    private final SchemaAccessControlRule schemaAccessControlRule;
    private final Optional<StringMatcher> catalogRegex;

    @JsonCreator
    public CatalogSchemaAccessControlRule(
            @JsonProperty("owner") boolean owner,
            @JsonProperty("user") Optional<StringMatcher> userRegex,
            @JsonProperty("role") Optional<StringMatcher> roleRegex,
            @JsonProperty("group") Optional<StringMatcher> groupRegex,
            @JsonProperty("schema") Optional<StringMatcher> schemaRegex,
            @JsonProperty("catalog") Optional<StringMatcher> catalogRegex)
    {
        this.schemaAccessControlRule = new SchemaAccessControlRule(owner, userRegex, roleRegex, groupRegex, schemaRegex);
        this.catalogRegex = requireNonNull(catalogRegex, "catalogRegex is null");
    }

    private CatalogSchemaAccessControlRule(SchemaAccessControlRule schemaAccessControlRule, Optional<StringMatcher> catalogRegex)
    {
        this.schemaAccessControlRule = schemaAccessControlRule;
        this.catalogRegex = catalogRegex;
    }

    public Optional<Boolean> match(String user, Set<String> roles, Set<String> groups, CatalogSchemaName schema)
    {
        if (!catalogRegex.map(regex -> regex.matches(schema.getCatalogName())).orElse(true)) {
            return Optional.empty();
        }
        return schemaAccessControlRule.match(user, roles, groups, schema.getSchemaName());
    }

    Optional<AnyCatalogPermissionsRule> toAnyCatalogPermissionsRule()
    {
        if (!schemaAccessControlRule.isOwner()) {
            return Optional.empty();
        }
        return Optional.of(new AnyCatalogPermissionsRule(
                schemaAccessControlRule.getUserRegex(),
                schemaAccessControlRule.getRoleRegex(),
                schemaAccessControlRule.getGroupRegex(),
                catalogRegex));
    }

    Optional<AnyCatalogSchemaPermissionsRule> toAnyCatalogSchemaPermissionsRule()
    {
        if (!schemaAccessControlRule.isOwner()) {
            return Optional.empty();
        }
        return Optional.of(new AnyCatalogSchemaPermissionsRule(
                schemaAccessControlRule.getUserRegex(),
                schemaAccessControlRule.getRoleRegex(),
                schemaAccessControlRule.getGroupRegex(),
                catalogRegex,
                schemaAccessControlRule.getSchemaRegex()));
    }
}
