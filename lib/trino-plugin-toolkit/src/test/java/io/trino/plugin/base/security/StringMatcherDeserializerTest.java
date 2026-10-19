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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules classes declare their patterns as {@code Optional<StringMatcher>}, so the
 * implementation has to be chosen by Jackson while unwrapping the {@link Optional}.
 */
public class StringMatcherDeserializerTest
{
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new Jdk8Module()).build();

    record Holder(@JsonProperty("user") Optional<StringMatcher> user)
    {
        @JsonCreator
        Holder {}
    }

    @Test
    void shouldChooseTheLiteralMatcherThroughOptional()
            throws Exception
    {
        Holder holder = mapper.readValue("{\"user\": \"alice\"}", Holder.class);

        assertThat(holder.user().isPresent()).isTrue();
        assertThat(holder.user().get()).isInstanceOf(LiteralStringMatcher.class);
        assertThat(holder.user().get().matches("alice")).isTrue();
    }

    @Test
    void shouldChooseTheRegexMatcherThroughOptional()
            throws Exception
    {
        Holder holder = mapper.readValue("{\"user\": \"svc-.*\"}", Holder.class);

        assertThat(holder.user().isPresent()).isTrue();
        assertThat(holder.user().get()).isInstanceOf(RegexStringMatcher.class);
        assertThat(holder.user().get().matches("svc-loader")).isTrue();
    }

    @Test
    void shouldLeaveAnAbsentPatternEmpty()
            throws Exception
    {
        Holder holder = mapper.readValue("{}", Holder.class);

        assertThat(holder.user()).isEqualTo(Optional.empty());
    }
}
