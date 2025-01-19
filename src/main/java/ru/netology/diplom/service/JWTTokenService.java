package ru.netology.diplom.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.netology.diplom.dto.UserDTO;
import ru.netology.diplom.model.Token;
import ru.netology.diplom.repository.UsersRepository;
import ru.netology.diplom.security.JWTToken;

@Service
@RequiredArgsConstructor
@Slf4j
public class JWTTokenService {
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTToken jwtToken;

    public Token login(UserDTO userDTO) {
        log.info("ИЩЕМ ПОЛЬЗОВАТЕЛЯ ПО ЛОГИНУ", userDTO.getLogin());
        var userFound = usersRepository.findByLogin(userDTO.getLogin())
                .orElseThrow(() -> new UsernameNotFoundException("ТАКОГО ПОЛЬЗОВАТЕЛЯ НЕТ В БАЗЕ ДАННЫХ"));
        log.info("ПОЛЬЗОВАТЕЛЬ НАЙДЕН ЕГО ДАННЫЕ:", userDTO.getLogin(), userFound);

        if (passwordEncoder.matches(userDTO.getPassword(), userFound.getPassword())) {
            String token = jwtToken.generateToken(userFound);
            return new Token(token);
        } else {
            throw new UsernameNotFoundException("ПОЛЬЗОВАТЕЛЬ С ТАКИМ ПАРОЛЕМ НЕ НАЙДЕН");
        }

    }
}
