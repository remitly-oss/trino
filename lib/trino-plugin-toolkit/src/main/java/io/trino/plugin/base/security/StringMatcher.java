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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Set;

/**
 * Matches a single name (user, group, role, catalog, schema, table, ...) against a
 * pattern taken from the access control rules file.
 * <p>
 * Upstream Trino stores every one of these patterns as a {@link java.util.regex.Pattern}
 * and evaluates it with {@code pattern.matcher(value).matches()}. The overwhelming
 * majority of patterns in a real rules file are plain literals, and a full-match regex
 * against a literal is far more expensive than {@link String#equals}. This interface lets
 * the deserializer pick the cheapest implementation that is guaranteed to behave
 * identically: {@link LiteralStringMatcher} when the pattern contains only characters that
 * cannot possibly be regex metacharacters, {@link RegexStringMatcher} otherwise.
 */
@JsonDeserialize(using = StringMatcherDeserializer.class)
public sealed interface StringMatcher
        permits LiteralStringMatcher, RegexStringMatcher
{
    /**
     * Returns the matcher that is cheapest for {@code pattern} while remaining
     * behaviourally identical to {@code Pattern.compile(pattern).matcher(value).matches()}.
     */
    static StringMatcher of(String pattern)
    {
        if (LiteralStringMatcher.isSafeLiteral(pattern)) {
            return new LiteralStringMatcher(pattern);
        }
        return new RegexStringMatcher(pattern);
    }

    /**
     * Whether {@code value} matches this pattern in its entirety.
     */
    boolean matches(String value);

    /**
     * Whether any element of {@code values} matches this pattern. Rules match roles and
     * groups this way, so this is the hot path for identities with many group memberships.
     */
    default boolean matchesAny(Set<String> values)
    {
        for (String value : values) {
            if (matches(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pattern exactly as it appeared in the rules file.
     */
    String pattern();
}
