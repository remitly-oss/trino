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

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Matches with a full-match regular expression, exactly as upstream Trino does.
 */
public final class RegexStringMatcher
        implements StringMatcher
{
    private final Pattern pattern;

    public RegexStringMatcher(String pattern)
    {
        this(Pattern.compile(requireNonNull(pattern, "pattern is null")));
    }

    public RegexStringMatcher(Pattern pattern)
    {
        this.pattern = requireNonNull(pattern, "pattern is null");
    }

    @Override
    public boolean matches(String value)
    {
        return pattern.matcher(value).matches();
    }

    @Override
    public String pattern()
    {
        return pattern.pattern();
    }

    /**
     * {@link Pattern} does not implement {@code equals}, so compare the source and flags,
     * matching how upstream Trino deduplicates rules.
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegexStringMatcher that)) {
            return false;
        }
        return pattern.pattern().equals(that.pattern.pattern()) && pattern.flags() == that.pattern.flags();
    }

    @Override
    public int hashCode()
    {
        return pattern.pattern().hashCode() * 31 + pattern.flags();
    }

    @Override
    public String toString()
    {
        return pattern.pattern();
    }
}
