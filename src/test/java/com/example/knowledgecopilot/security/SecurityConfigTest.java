package com.example.knowledgecopilot.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigTest {

    @Test
    void mapsRealmRolesToRoleAuthorities() {
        KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "kc_user")
            .claim("realm_access", Map.of("roles", List.of("USER", "ROLE_ADMIN")))
            .build();

        Collection<GrantedAuthority> converted = converter.convert(jwt);

        Set<String> authorities = converted.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"), authorities);
    }
}
