
package com.example.careerMonitor.UserService;

import com.example.careerMonitor.Entity.User;
import com.example.careerMonitor.repository.UserRepo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepo userRepository;

    public UserService(UserRepo userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User getOrCreateUser(Jwt jwt) {

        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {

                    User user = new User();

                    user.setKeycloakId(keycloakId);
                    user.setName(name);
                    user.setEmail(email);

                    return userRepository.save(user);
                });
    }
}
