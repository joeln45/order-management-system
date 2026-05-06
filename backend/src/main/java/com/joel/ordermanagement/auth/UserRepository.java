package com.joel.ordermanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /** Used during login: look up by the supplied username. */
    Optional<User> findByUsername(String username);

    /** Used during registration: fast existence check before insert. */
    boolean existsByUsername(String username);
}
