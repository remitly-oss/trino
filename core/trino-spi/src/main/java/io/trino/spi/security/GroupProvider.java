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
package io.trino.spi.security;

import java.util.HashSet;
import java.util.Set;

public interface GroupProvider
{
    Set<String> getGroups(String user);

    /**
     * Resolve the complete group set for a session identity. The principal comes from
     * authentication, while the user may be an impersonated user. Implementations may
     * replace (not merely augment) groups to constrain impersonated sessions.
     */
    default Set<String> resolveGroups(Identity identity)
    {
        Set<String> groups = new HashSet<>(identity.getGroups());
        groups.addAll(getGroups(identity.getUser()));
        return Set.copyOf(groups);
    }
}
