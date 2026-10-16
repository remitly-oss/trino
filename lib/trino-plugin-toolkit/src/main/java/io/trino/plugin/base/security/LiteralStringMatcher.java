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

import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Matches by string equality. Only used for patterns that {@link #isSafeLiteral} accepts,
 * i.e. patterns that cannot contain a regex metacharacter, for which a full-match regex is
 * equivalent to {@link String#equals}.
 */
public record LiteralStringMatcher(String literal)
        implements StringMatcher
{
    /**
     * Characters that carry no special meaning in a regular expression, so a pattern built
     * exclusively from them matches itself and nothing else.
     * <p>
     * This is deliberately conservative: widening it is safe only for characters that are
     * neither metacharacters themselves nor able to change the meaning of a neighbouring
     * character. Anything rejected here still works, just via {@link RegexStringMatcher}.
     */
    private static final Pattern SAFE_LITERAL = Pattern.compile("[a-zA-Z0-9_-]+");

    public LiteralStringMatcher
    {
        requireNonNull(literal, "literal is null");
    }

    /**
     * Whether {@code pattern} is guaranteed to be a plain literal rather than a regex.
     */
    public static boolean isSafeLiteral(String pattern)
    {
        return SAFE_LITERAL.matcher(pattern).matches();
    }

    @Override
    public boolean matches(String value)
    {
        return literal.equals(value);
    }

    @Override
    public boolean matchesAny(Set<String> values)
    {
        // A hash lookup against the (typically much larger) set of roles or groups, instead
        // of one regex evaluation per element.
        return values.contains(literal);
    }

    @Override
    public String pattern()
    {
        return literal;
    }

    @Override
    public String toString()
    {
        return literal;
    }
}
