package com.dgcockpit.service;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.UserToken;
import com.dgcockpit.repository.AppUserRepository;
import com.dgcockpit.repository.UserTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository userRepo;
    private final UserTokenRepository tokenRepo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository userRepo, UserTokenRepository tokenRepo) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
    }

    public AppUser login(String username, String password) {
        AppUser user = userRepo.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Identifiants incorrects"));
        if (!user.isActif())
            throw new RuntimeException("Ce compte est désactivé");
        if (!encoder.matches(password, user.getPasswordHash()))
            throw new RuntimeException("Identifiants incorrects");
        return user;
    }

    @Transactional
    public String createToken(AppUser user) {
        tokenRepo.invalidateAllForUser(user.getId());
        UserToken t = new UserToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUser(user);
        t.setExpiresAt(LocalDateTime.now().plusDays(30));
        tokenRepo.save(t);
        return t.getToken();
    }

    public Optional<AppUser> validateToken(String rawToken) {
        return tokenRepo.findByTokenAndValidTrue(rawToken)
            .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
            .map(UserToken::getUser)
            .filter(AppUser::isActif);
    }

    @Transactional
    public void logout(String rawToken) {
        tokenRepo.findByToken(rawToken).ifPresent(t -> {
            t.setValid(false);
            tokenRepo.save(t);
        });
    }

    public String hashPassword(String plain) {
        return encoder.encode(plain);
    }
}
