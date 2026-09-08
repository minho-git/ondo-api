package com.ondo.retail.storage;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 디렉터리에 저장한다. 개발용이다.
 *
 * <p>배포에서는 안 뜬다 (MUL-100). Fargate 컨테이너 디스크는 태스크가 갈릴 때 사라지고,
 * 태스크가 둘이라 A 에 올린 파일을 B 가 못 읽는다. 배포는 {@link S3FileStorage} 가 맡는다.
 *
 * <p>{@code !deploy} 로 건 이유 — 로컬 프로필은 {@code local} 이고 테스트도 그걸 쓴다.
 * 배포만 정확히 빼면 되고, 그래야 팀원이 AWS 자격증명 없이 돌릴 수 있다.
 * 두 구현체의 조건이 서로 여집합이라 항상 정확히 하나만 뜬다 — 빈이 없거나 둘이 겹쳐서
 * 기동에 실패하는 일이 없다.
 */
@Slf4j
@Component
@Profile("!deploy")
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
