package com.applyflow.security;

import com.applyflow.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        com.applyflow.entity.User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles("USER").build();
    }
}
