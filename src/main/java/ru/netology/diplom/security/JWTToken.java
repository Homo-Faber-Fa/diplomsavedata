package ru.netology.diplom.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.netology.diplom.details.UserPrincipal;
import ru.netology.diplom.entity.UserEntity;
import ru.netology.diplom.service.UserService;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JWTToken {

    private final SecretKey secret;
    private final int tokenLifetime;
    private final List<String> listTokens = new ArrayList<>();
    private final UserService userService;

    public JWTToken(@Value("${jwt.secret}") String secret, @Value("${TOKEN_LIFETIME}") int tokenLifetime, UserService userService) {
        this.secret = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.tokenLifetime = tokenLifetime;
        this.userService = userService;
    }

    public UserPrincipal getAuthenticatedUser() {
        var au = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return userService.loadUserByUsername(au);
    }

    public String generateToken(@NonNull UserEntity userEntity) throws IllegalArgumentException {
        //this.userEntity = userEntity;
        Date now = new Date();
        Date exp = Date.from(LocalDateTime.now().plusMinutes(tokenLifetime)
                .atZone(ZoneId.systemDefault()).toInstant());

        String token = Jwts.builder()
                .setId(String.valueOf(userEntity.getId()))
                .setSubject(userEntity.getLogin())
                .setIssuedAt(now)
                .setNotBefore(now)
                .setExpiration(exp)
                .setHeader(Map.of("type", "JWT"))
                .signWith(secret, SignatureAlgorithm.HS256)
                .claim("roles", userEntity)
                .compact();
        log.info("T ДОБАВЛЕН В СПИСОК АКТИВНЫХ ТОКЕНОВ", token);
        listTokens.add(token);
        return token;
    }

    public boolean validateAccessToken(@NonNull String token) {
        for (String t : listTokens) {
            if (t.equals(token)) {
                return validateToken(token, secret);
            }
        }
        return false;
    }

    private boolean validateToken(@NonNull String token, @NonNull Key secret) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secret)
                    .build()
                    .parseClaimsJws(token);

            return true;
        } catch (ExpiredJwtException expEx) {
            log.error("СРОК ДЕЙСТВИЯ ТОКЕНА ИСТЕК", expEx);
        } catch (UnsupportedJwtException unsEx) {
            log.error("ФОРМА ТОКЕНА НЕПОДДЕРЖИВАЕТСЯ", unsEx);
        } catch (MalformedJwtException mjEx) {
            log.error("ФОРМА ТОКЕНА НЕКОРРЕКТНА ДЛЯ jwt", mjEx);
        } catch (SignatureException sEx) {
            log.error("НЕДЕЙСТВИТЕЛЬНАЯ ПОДПИСЬ", sEx);
        } catch (Exception e) {
            log.error("НЕДОПУСТИМЫЙ ТОКЕН", e);
        }
        return false;
    }

    public Claims getAccessClaims(@NonNull String token) {
        return getClaims(token, secret);
    }

    private Claims getClaims(@NonNull String token, @NonNull Key secret) {
        return Jwts.parserBuilder()
                .setSigningKey(secret)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
