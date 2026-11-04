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
import io.trino.spi.security.GroupProvider;
import io.trino.spi.security.Identity;

public final class ViewIdentityResolver
{
    private ViewIdentityResolver() {}

    public static Identity resolveViewIdentity(GroupProvider groupProvider, Identity owner, Identity invoker)
    {
        if (owner.getUser().equals(invoker.getUser())) {
            // A same-name definer must not upgrade a constrained invoker back to
            // normal groups. Retain authenticated context for contextual resolution.
            Identity identity = Identity.from(owner)
                    .withPrincipal(invoker.getPrincipal())
                    .withGroups(ImmutableSet.of())
                    .build();
            return Identity.from(identity)
                    .withGroups(groupProvider.resolveGroups(identity))
                    .build();
        }
        // Different-user SECURITY DEFINER behavior is unchanged.
        return Identity.from(owner)
                .withGroups(groupProvider.getGroups(owner.getUser()))
                .build();
    }
}
