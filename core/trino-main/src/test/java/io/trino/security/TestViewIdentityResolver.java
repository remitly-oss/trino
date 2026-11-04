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
package io.trino.security;

import com.google.common.collect.ImmutableSet;
import io.trino.spi.security.BasicPrincipal;
import io.trino.spi.security.GroupProvider;
import io.trino.spi.security.Identity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestViewIdentityResolver
{
    private static final GroupProvider GROUP_PROVIDER = new GroupProvider()
    {
        @Override
        public Set<String> getGroups(String user)
        {
            return ImmutableSet.of("normal-admin");
        }

        @Override
        public Set<String> resolveGroups(Identity identity)
        {
            if (identity.getPrincipal().map(principal -> principal.getName().equals("hex")).orElse(false)) {
                return ImmutableSet.of("squad-TEST-vis");
            }
            return GroupProvider.super.resolveGroups(identity);
        }
    };

    @Test
    void testSameNameDefinerDoesNotRestoreNormalGroups()
    {
        Identity invoker = Identity.forUser("alice")
                .withPrincipal(new BasicPrincipal("hex"))
                .withGroups(ImmutableSet.of("squad-TEST-vis"))
                .build();
        Identity owner = Identity.forUser("alice").withGroups(ImmutableSet.of("normal-admin")).build();
        Identity resolved = ViewIdentityResolver.resolveViewIdentity(GROUP_PROVIDER, owner, invoker);
        assertThat(resolved.getUser()).isEqualTo("alice");
        assertThat(resolved.getGroups()).containsExactly("squad-TEST-vis");
        assertThat(resolved.getPrincipal()).contains(new BasicPrincipal("hex"));
    }

    @Test
    void testDifferentDefinerKeepsExistingBehavior()
    {
        Identity invoker = Identity.forUser("alice").withPrincipal(new BasicPrincipal("hex")).build();
        Identity resolved = ViewIdentityResolver.resolveViewIdentity(GROUP_PROVIDER, Identity.ofUser("bob"), invoker);
        assertThat(resolved.getUser()).isEqualTo("bob");
        assertThat(resolved.getGroups()).containsExactly("normal-admin");
        assertThat(resolved.getPrincipal()).isEmpty();
    }

    @Test
    void testNormalSameNameDefinerKeepsNormalGroups()
    {
        Identity invoker = Identity.forUser("alice").withPrincipal(new BasicPrincipal("alice")).build();
        Identity resolved = ViewIdentityResolver.resolveViewIdentity(GROUP_PROVIDER, Identity.ofUser("alice"), invoker);
        assertThat(resolved.getGroups()).containsExactly("normal-admin");
    }
}
