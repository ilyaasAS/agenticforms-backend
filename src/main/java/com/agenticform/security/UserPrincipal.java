package com.agenticform.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.agenticform.model.entity.User;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final int tokenVersion;
    private final boolean blocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(
            Long id,
            String email,
            String password,
            int tokenVersion,
            boolean blocked,
            Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.tokenVersion = tokenVersion;
        this.blocked = blocked;
        this.authorities = authorities;
    }

    public static UserPrincipal from(User user) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(user.getRole().name()));
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getTokenVersion(),
                user.isBlocked(),
                authorities);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !blocked;
    }
}
