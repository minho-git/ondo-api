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
    public String store(MultipartFile file, String directory, FileType type) {
        // 이름도 확장자도 우리가 정한다. 사용자가 준 이름은 안 쓴다 —
        // 경로 조작(../)이나 실행 가능한 확장자가 섞일 자리를 아예 없앤다
        String name = UUID.randomUUID() + type.extension();

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

}
