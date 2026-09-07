package com.ondo.retail.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일을 저장한다.
 *
 * <p>지금은 로컬 디렉터리에 쓴다. 배포하면 컨테이너가 재시작될 때 파일이 사라지므로
 * S3 구현체로 갈아끼운다(MUL-77). 이 인터페이스만 지키면 서비스 코드는 안 고친다.
 */
public interface FileStorage {

    /**
     * @param directory 논리적 묶음. 예: {@code retailer/12}
     * @param type      파일 앞머리로 확인한 <b>진짜</b> 형식 (MUL-99).
     *                  확장자를 여기서 정한다 — 사용자가 준 이름을 안 믿는다
     * @return 나중에 파일을 찾을 때 쓰는 경로. retailer_doc.file_url 에 그대로 들어간다
     */
    String store(MultipartFile file, String directory, FileType type);
}
