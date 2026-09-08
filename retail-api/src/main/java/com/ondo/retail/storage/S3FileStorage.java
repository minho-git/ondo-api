package com.ondo.retail.storage;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 가입 서류를 S3 에 저장한다 (MUL-100).
 *
 * <p>전에는 배포에서도 {@link LocalFileStorage} 가 컨테이너 안에 썼다. Fargate 는 태스크가
 * 갈릴 때 디스크가 사라지므로 <b>배포할 때마다 그동안 올라온 사업자등록증이 날아갔다.</b>
 * 태스크가 둘인 것도 문제였다 — 가입 요청이 A 로 가면 파일은 A 에만 생겨서, 나중에 B 가
 * 그 파일을 못 읽는다.
 *
 * <p>버킷은 MUL-77 에서 이미 만들어 뒀다({@code aws_s3_bucket.documents}). 암호화 ·
 * 퍼블릭 차단이 켜져 있고 쓰는 코드만 없었다.
 *
 * <p><b>자격증명을 코드가 안 다룬다.</b> ECS 태스크 역할이 임시 자격증명을 주고 SDK 가
 * 알아서 받아 쓴다(infra/iam.tf 의 {@code task_retail}). 키를 파일이나 환경변수에 두지
 * 않는다 — MUL-105 에서 GitHub 에 액세스 키를 안 넣은 것과 같은 판단이다.
 */
@Slf4j
@Component
@Profile("deploy")
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, @Value("${ondo.storage.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /**
     * {@inheritDoc}
     *
     * <p>돌려주는 값은 <b>버킷 안의 키</b>다 — {@code retailer/12/a3f9-….jpg}.
     * 버킷 이름도 리전도 안 붙인다. 그 둘이 {@code retailer_doc.file_url} 에 박히면
     * 나중에 버킷을 옮길 때 데이터를 전부 갱신해야 한다. 12월에 AWS 계정을 개인 것으로
     * 옮길 예정이라 더 그렇다.
     *
     * <p>공개 URL 도 안 쓴다. 증빙 서류라 버킷을 절대 안 열기 때문이다(infra/s3.tf).
     * 운영자가 봐야 할 때는 이 키로 짧은 presigned URL 을 만들어 준다 — 그 API 는 아직 없다.
     */
    @Override
    public String store(MultipartFile file, String directory, FileType type) {
        // 이름도 확장자도 우리가 정한다. 사용자가 준 이름은 안 쓴다 —
        // 경로 조작(../)이나 실행 가능한 확장자가 섞일 자리를 아예 없앤다.
        //
        // UUID 라 추측이 안 된다. retailer/12 까지는 순번이라 짐작할 수 있어도
        // 파일 이름을 못 맞히면 남의 서류를 훑을 수 없다. 태스크 역할에 ListBucket 을
        // 안 준 것도 같은 이유다 — 목록을 못 받으니 열거가 불가능하다
        String key = directory + "/" + UUID.randomUUID() + type.extension();

        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            // 브라우저가 준 Content-Type 을 그대로 안 쓴다. 그건 조작되는
                            // 값이라 MUL-99 에서 이미 안 믿기로 했다. 파일 앞머리로 확인한
                            // 진짜 형식을 적는다
                            .contentType(type.contentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("파일 저장. bucket={} key={}", bucket, key);
            return key;

        } catch (IOException | S3Exception e) {
            // 버킷 이름 · 키를 응답에 실어 보내지 않는다. 내부 구조를 알려줄 이유가 없다
            log.error("파일 저장 실패. bucket={} key={}", bucket, key, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
