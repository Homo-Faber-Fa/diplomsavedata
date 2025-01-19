package ru.netology.diplom.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.netology.diplom.dto.CloudFileDto;
import ru.netology.diplom.entity.CloudFileEntity;
import ru.netology.diplom.entity.UserEntity;
import ru.netology.diplom.repository.CloudRepository;
import ru.netology.diplom.security.JWTToken;
import ru.netology.diplom.util.CloudManager;

import java.io.FileNotFoundException;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudService {


    private final JWTToken jwtToken;
    private final CloudRepository cloudRepository;
    private final CloudManager cloudManager;

    private static void fileNotFound(String msg) throws FileNotFoundException {
        log.error(msg);
        throw new FileNotFoundException(msg);
    }

    private static void fileAlreadyExists(String msg) throws FileAlreadyExistsException {
        log.error(msg);
        throw new FileAlreadyExistsException(msg);
    }

    @SneakyThrows
    @Transactional()
    public boolean uploadFile(MultipartFile multipartFile, String filename) {
        Optional<CloudFileEntity> cloudFile = getCloudFileEntity(filename);
        if (cloudFile.isPresent()) {
            log.info("ФАЙЛ ЕСТЬ в БД МЕНЯЕМ ИМЯ", filename);
            String renameFile = filename;
            var indexPoint = filename.indexOf(".");
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                renameFile = String.format(filename.substring(0, indexPoint) + filename.substring(indexPoint), i);
                cloudFile = getCloudFileEntity(renameFile);
                if (cloudFile.isEmpty()) {
                    break;
                }
            }
            filename = renameFile;
        }

        log.info("ЗАПИСЫВАЮ", filename);
        CloudFileEntity cloudFileEntity = CloudFileEntity.builder()
                .filename(filename)
                .size(multipartFile.getSize())
                .date(Instant.now())
                .key(UUID.randomUUID())
                .userEntity(
                        UserEntity.builder()
                                .id(jwtToken.getAuthenticatedUser().getId())
                                .build())
                .build();

        var cloudId = cloudRepository.save(cloudFileEntity).getId();
        if (cloudRepository.findById(cloudId).isPresent()) {
            log.info("ФАЙЛ ЗАПИСАН", filename, cloudId);
        }
        if (!cloudManager.upload(multipartFile.getBytes(),
                cloudFileEntity.getKey().toString(),
                cloudFileEntity.getFilename())) {
            fileNotFound("ФАЙЛ НЕ ЗАПИСАН");
        }
        log.info("ФАЙЛ ЗАПИСАН");
        return true;
    }

    @SneakyThrows
    @Transactional
    public CloudFileDto getFile(String filename) {
        Optional<CloudFileEntity> cloudFile = getCloudFileEntity(filename);
        if (cloudFile.isPresent()) {
            log.info("ФАЙЛ НАЙДЕН", filename);
            var resourceFromBd = cloudFile.map(cloudManager::getFile).get();
            return CloudFileDto.builder()
                    .filename(filename)
                    .resource(resourceFromBd)
                    .build();
        } else {
            fileNotFound("ФАЙЛ НЕ НАЙДЕН");
            return null;
        }
    }

    @SneakyThrows
    @Transactional()
    public boolean deleteFile(String filename) {
        Optional<CloudFileEntity> foundFile = getCloudFileEntity(filename);
        if (foundFile.isEmpty()) {
            String msg = String.format("ФАЙЛ НЕСУЩЕСТВУЕТ", filename);
            log.info(msg);
            throw new FileNotFoundException(msg);
        }
        int idFoundFile = foundFile.get().getId();
        cloudRepository.deleteById(idFoundFile);
        log.info("УДАЛЕН", filename);
        if (cloudRepository.findById(idFoundFile).isPresent()) {
            fileAlreadyExists("ФАЙЛ НЕ УДАЛЕН");
        }
        if (!cloudManager.delete(foundFile.get())) {
            fileAlreadyExists("ФАЙЛ НЕ УДАЛЕН");
        }
        return true;
    }

    @SneakyThrows
    @Transactional()
    public boolean putFile(String filename, CloudFileDto cloudFileDto) {
        var cloudFile = getCloudFileEntity(filename);
        if (cloudFile.isEmpty()) {
            fileNotFound("ФАЙЛ НЕ НАЙДЕН");
        }
        if (getCloudFileEntity(cloudFileDto.getFilename()).isPresent()) {
            fileAlreadyExists("ФАЙЛ НАЙДЕН");
        }
        cloudRepository.updateFilenameByUserEntityId(cloudFileDto.getFilename(), cloudFile.get().getId());
        if (!cloudManager.renameFileTo(cloudFile.get(), cloudFileDto.getFilename())) {
            fileNotFound("Не удалось переименовать файл на сервере");
        }

        return true;
    }

    public List<CloudFileDto> getAllFile() {
        var cloudFileEntityList = cloudRepository.findAll();
        return cloudFileEntityList.stream()
                .map(file -> CloudFileDto.builder()
                        .filename(file.getFilename())
                        .key(file.getKey())
                        .date(file.getDate())
                        .size(file.getSize())
                        .build())
                .collect(Collectors.toList());
    }

    private Optional<CloudFileEntity> getCloudFileEntity(String filename) {
        int userId = jwtToken.getAuthenticatedUser().getId();
        log.info("ПОЛУЧАЕМ ID ПОЛЬЗОВАТЕЛЯ ПО ТОКЕНУ", userId);
        log.info("НАЧИНАЕМ ИСКАТЬ ФАЙЛ В БД", filename);
        return cloudRepository.findByFilename(filename);
    }


}
