package com.ondo.retail.storage;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 디렉터리에 저장한다. 개발용이다. */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${ondo.storage.local-root:./var/uploads}") String root) {
        this.root = Path.of(root);
    }

    @Override
    public String store(MultipartFile file, String directory) {
        String extension = extensionOf(file.getOriginalFilename());
        String name = UUID.randomUUID() + extension;

        try {
            Path target = root.resolve(directory).resolve(name);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
            log.info("파일 저장. path={}", target);
            return target.toString();
        } catch (IOException e) {
            log.error("파일 저장 실패. directory={}", directory, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 원본 이름은 사용자가 정한 것이라 그대로 쓰지 않는다. 확장자만 떼어 쓴다. */
    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot).toLowerCase();
    }
}
