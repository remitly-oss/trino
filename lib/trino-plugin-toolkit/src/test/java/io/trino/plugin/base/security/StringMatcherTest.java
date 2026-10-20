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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class StringMatcherTest
{
    /**
     * Values used to prove that whichever matcher is chosen, the decision is unchanged.
     */
    private static final List<String> SAMPLE_VALUES = List.of(
            "",
            "a",
            "alice",
            "Alice",
            "alice-2",
            "alice_bob",
            "data-eng",
            "data",
            "aliceX",
            "xalice",
            "alice ",
            " alice",
            "a.ice",
            "aaa",
            "-",
            "_",
            "alice1",
            "alice12",
            "1",
            "2024",
            "20240",
            "0224");

    @Test
    void shouldUseLiteralMatcherForRegexFreePatterns()
    {
        for (String pattern : List.of("alice", "Alice", "ALICE", "a", "data-eng", "data_eng", "-", "_", "abcXYZ-_", "alice1", "svc1", "0", "2024", "team-42_b")) {
            assertThat(StringMatcher.of(pattern)).isInstanceOf(LiteralStringMatcher.class);
        }
    }

    @Test
    void shouldUseRegexMatcherForEverythingElse()
    {
        for (String pattern : List.of("", ".*", "alice.*", "a.b", "a|b", "a+", "a?", "[abc]", "(a)(b)", "^alice$", "alice@remitly.com", "svc.account", "a\\d", "user{2}")) {
            assertThat(StringMatcher.of(pattern)).isInstanceOf(RegexStringMatcher.class);
        }
    }

    @Test
    void shouldMatchExactlyWhatAFullMatchRegexWouldMatch()
    {
        for (String pattern : List.of("alice", "data-eng", "data_eng", "-", "_", "A", "alice1", "2024", ".*", "alice.*", "a|b", "[a-c]+")) {
            StringMatcher matcher = StringMatcher.of(pattern);
            Pattern reference = Pattern.compile(pattern);
            for (String value : SAMPLE_VALUES) {
                assertThat(matcher.matches(value))
                        .as("pattern '%s' disagreed on value '%s'", pattern, value)
                        .isEqualTo(reference.matcher(value).matches());
            }
        }
    }

    @Test
    void shouldAnchorLiteralMatchesAtBothEnds()
    {
        StringMatcher matcher = StringMatcher.of("alice");
        assertThat(matcher.matches("alice")).isTrue();
        assertThat(matcher.matches("alice-2")).isFalse();
        assertThat(matcher.matches("malice")).isFalse();
        assertThat(matcher.matches("ALICE")).isFalse();
        assertThat(matcher.matches("")).isFalse();
    }

    @Test
    void shouldMatchAnyMemberOfASetByLookupForLiterals()
    {
        StringMatcher matcher = StringMatcher.of("data-eng");
        assertThat(matcher).isInstanceOf(LiteralStringMatcher.class);
        assertThat(matcher.matchesAny(Set.of("platform", "data-eng", "analytics"))).isTrue();
        assertThat(matcher.matchesAny(Set.of("platform", "analytics"))).isFalse();
        assertThat(matcher.matchesAny(Set.of())).isFalse();
    }

    @Test
    void shouldMatchAnyMemberOfASetForRegexes()
    {
        StringMatcher matcher = StringMatcher.of("data-.*");
        assertThat(matcher).isInstanceOf(RegexStringMatcher.class);
        assertThat(matcher.matchesAny(Set.of("platform", "data-eng"))).isTrue();
        assertThat(matcher.matchesAny(Set.of("platform", "analytics"))).isFalse();
        assertThat(matcher.matchesAny(Set.of())).isFalse();
    }

    @Test
    void shouldAgreeWithRegexOnSetMatching()
    {
        Set<String> groups = Set.of("platform", "data-eng", "analytics");
        for (String pattern : List.of("data-eng", "platform", "missing", "data-.*", "a|b")) {
            StringMatcher matcher = StringMatcher.of(pattern);
            Pattern reference = Pattern.compile(pattern);
            assertThat(matcher.matchesAny(groups))
                    .as("pattern '%s' disagreed on set matching", pattern)
                    .isEqualTo(groups.stream().anyMatch(group -> reference.matcher(group).matches()));
        }
    }

    /**
     * The permission rules derived from the rules file are deduplicated in a set, so matchers
     * built from the same pattern have to be interchangeable as map keys.
     */
    @Test
    void shouldBeValueEqual()
    {
        assertThat(StringMatcher.of("alice")).isEqualTo(StringMatcher.of("alice"));
        assertThat(StringMatcher.of("alice").hashCode()).isEqualTo(StringMatcher.of("alice").hashCode());
        assertThat(StringMatcher.of("alice.*")).isEqualTo(StringMatcher.of("alice.*"));
        assertThat(StringMatcher.of("alice.*").hashCode()).isEqualTo(StringMatcher.of("alice.*").hashCode());
        assertThat(StringMatcher.of("bob")).isNotEqualTo(StringMatcher.of("alice"));
        assertThat(StringMatcher.of("bob.*")).isNotEqualTo(StringMatcher.of("alice.*"));
        assertThat(new HashSet<>(List.of(StringMatcher.of("alice"), StringMatcher.of("alice"))).size()).isEqualTo(1);
        assertThat(new HashSet<>(List.of(StringMatcher.of("a.*"), StringMatcher.of("a.*"))).size()).isEqualTo(1);
    }

    @Test
    void shouldMatchIdentitiesWithLiteralAndRegexMatchers()
    {
        IdentityMatcher literal = new IdentityMatcher(Optional.of(StringMatcher.of("alice")), Optional.of(StringMatcher.of("reader")), Optional.of(StringMatcher.of("data-eng")));
        assertThat(literal.matches("alice", Set.of("reader", "writer"), Set.of("data-eng", "platform"))).isTrue();
        assertThat(literal.matches("alice", Set.of(), Set.of("data-eng"))).isFalse();
        assertThat(literal.matches("alice", Set.of("reader"), Set.of())).isFalse();
        assertThat(literal.matches("bob", Set.of("reader"), Set.of("data-eng"))).isFalse();

        IdentityMatcher regex = new IdentityMatcher(Optional.of(StringMatcher.of("a.*")), Optional.of(StringMatcher.of("read.*")), Optional.of(StringMatcher.of("data-.*")));
        assertThat(regex.matches("alice", Set.of("reader"), Set.of("data-eng"))).isTrue();
        assertThat(regex.matches("alice", Set.of("writer"), Set.of("data-eng"))).isFalse();
        assertThat(new IdentityMatcher(Optional.empty(), Optional.empty(), Optional.empty()).matches("anyone", Set.of(), Set.of())).isTrue();
    }

    @Test
    void shouldPreserveIdentityMatcherEqualityAndPatternFlags()
    {
        IdentityMatcher first = new IdentityMatcher(Optional.of(StringMatcher.of("alice")), Optional.of(StringMatcher.of("read.*")), Optional.empty());
        IdentityMatcher second = new IdentityMatcher(Optional.of(StringMatcher.of("alice")), Optional.of(StringMatcher.of("read.*")), Optional.empty());
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(new HashSet<>(List.of(first, second))).hasSize(1);

        IdentityMatcher caseInsensitive = new IdentityMatcher(Optional.of(new RegexStringMatcher(Pattern.compile("alice", Pattern.CASE_INSENSITIVE))), Optional.empty(), Optional.empty());
        IdentityMatcher caseSensitive = new IdentityMatcher(Optional.of(new RegexStringMatcher(Pattern.compile("alice"))), Optional.empty(), Optional.empty());
        assertThat(caseInsensitive.matches("ALICE", Set.of(), Set.of())).isTrue();
        assertThat(caseSensitive.matches("ALICE", Set.of(), Set.of())).isFalse();
        assertThat(caseInsensitive).isNotEqualTo(caseSensitive);
    }

    @Test
    void shouldPreserveUserSubstitutingPatterns()
    {
        for (String rawPattern : List.of("alice", "data-eng", "", "a.*", "(?i)alice", "^\\{user\\}$")) {
            UserSubstitutingPattern pattern = UserSubstitutingPattern.of(rawPattern);
            Pattern reference = Pattern.compile(rawPattern);
            for (String value : SAMPLE_VALUES) {
                assertThat(pattern.matches("different-user", value)).isEqualTo(reference.matcher(value).matches());
            }
        }
        UserSubstitutingPattern substituted = UserSubstitutingPattern.of("^{user}_(sandbox|scratch)$");
        assertThat(substituted.matches("alice", "alice_sandbox")).isTrue();
        assertThat(substituted.matches("bob", "bob_scratch")).isTrue();
        assertThat(substituted.matches("alice", "bob_sandbox")).isFalse();
        assertThat(substituted.matches("a.user", "a.user_sandbox")).isTrue();
        assertThat(substituted.matches("a.user", "aXuser_sandbox")).isFalse();
        assertThat(substituted.matches("\\E", "\\E_sandbox")).isTrue();
    }

    @Test
    void shouldExposeTheOriginalPattern()
    {
        assertThat(StringMatcher.of("alice").pattern()).isEqualTo("alice");
        assertThat(StringMatcher.of("alice.*").pattern()).isEqualTo("alice.*");
    }
}
