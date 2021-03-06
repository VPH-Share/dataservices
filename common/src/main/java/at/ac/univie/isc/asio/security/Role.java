/*
 * #%L
 * asio common
 * %%
 * Copyright (C) 2013 - 2015 Research Group Scientific Computing, University of Vienna
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package at.ac.univie.isc.asio.security;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthoritiesContainer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A {@code Role} represents a bundle of granted {@link Permission}s.
 */
public enum Role implements GrantedAuthority, GrantedAuthoritiesContainer {

  NONE(null), USER(Role.NONE, Permission.ACCESS_METADATA, Permission.INVOKE_QUERY), OWNER(Role.USER, Permission.ACCESS_INTERNALS, Permission.INVOKE_UPDATE), ADMIN(Role.OWNER, Permission.ADMINISTRATE)
  // alias for legacy compatibility
  , READ(Role.USER), FULL(Role.OWNER);

  public static final String PREFIX = "ROLE_";

  /**
   * Attempt to convert the given {@code String} into the Role with a matching name. Accept both
   * raw {@link Role#name()} and {@link Role#getAuthority()} values that match a defined role.
   *
   * @param name name or authority of the role
   * @return parsed role or {@link Role#NONE} if there is no role with that name
   */
  public static Role fromString(@Nullable final String name) {
    if (name == null) { return Role.NONE; }
    final Role found = LOOKUP.get(normalize(name));
    return found != null ? found : Role.NONE;
  }

  public static Role fromAuthority(@Nullable final GrantedAuthority authority) {
    if (authority == null) { return Role.NONE; }
    return fromString(authority.getAuthority());
  }

  private static String normalize(final String name) {
    if (name.startsWith(PREFIX)) {
      return name.substring(PREFIX.length()).toUpperCase(Locale.ENGLISH);
    } else {
      return name.toUpperCase(Locale.ENGLISH);
    }
  }

  private static final Map<String, Role> LOOKUP;

  static {
    final ImmutableMap.Builder<String, Role> builder = ImmutableMap.builder();
    for (Role each : Role.values()) {
      builder.put(each.name(), each);
    }
    LOOKUP = builder.build();
  }

  private final Set<Permission> permissions;
  private final String authority;

  private Role(final Role parent, final Permission... permissions) {
    final ImmutableSet<Permission> exclusive = ImmutableSet.copyOf(permissions);
    if (parent == null) {
      this.permissions = exclusive;
    } else {  // include parent's permissions
      this.permissions = ImmutableSet.copyOf(Sets.union(exclusive, parent.getGrantedAuthorities()));
    }
    authority = PREFIX + name();
  }

  /**
   * The authority name is the name of this role with a 'ROLE_' prefix.
   *
   * @return this role as authority name
   */
  @Override
  public String getAuthority() {
    return authority;
  }

  /**
   * Get the set of {@link at.ac.univie.isc.asio.security.Permission permissions} granted to this role.
   *
   * @return set of all granted permissions
   */
  @Override
  public Set<Permission> getGrantedAuthorities() {
    return permissions;
  }

  /**
   * Get a list of this role authority and all granted permissions. The first authority in the list
   * is this role, followed by the permissions in no specific order.
   *
   * @return set of all granted permissions plus this role itself
   */
  public List<GrantedAuthority> expand() {
    return ImmutableList.<GrantedAuthority>builder().add(this).addAll(permissions).build();
  }

  /**
   * @param permission Permission to be checked
   * @return true if this role grants the given permission
   */
  public boolean grants(final Permission permission) {
    requireNonNull(permission);
    return permissions.contains(permission) || Permission.ANY.equals(permission);
  }

  @Override
  public String toString() {
    return authority + "{permissions=" + permissions + '}';
  }
}
