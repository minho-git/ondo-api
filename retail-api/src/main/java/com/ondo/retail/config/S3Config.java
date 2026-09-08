package com.ondo.retail.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 클라이언트 (MUL-100).
 *
 * <p>배포에서만 만든다. 로컬은 {@code LocalFileStorage} 가 디스크에 쓰므로 이 빈이 없어도
 * 되고, 있으면 오히려 AWS 자격증명이 없는 팀원 노트북에서 기동이 막힌다.
 *
 * <p><b>자격증명을 여기서 안 준다.</b> SDK 기본 체인이 ECS 태스크 역할에서 임시 자격증명을
 * 받아온다 — 컨테이너에 ECS 가 알려준 주소가 있고 SDK 가 거기서 받아 만료 전에 갱신한다.
 * 키가 파일에도 환경변수에도 없다.
 */
@Configuration
@Profile("deploy")
public class S3Config {

    /**
     * 리전은 명시한다.
     *
     * <p>SDK 가 환경에서 알아내기도 하지만 ECS 가 늘 넣어주는 값이 아니다. 못 찾으면
     * 기동이 아니라 <b>첫 업로드에서</b> 터진다 — 그때는 이미 가입 요청이 들어온 뒤다.
     * 태스크 정의가 {@code AWS_REGION} 을 주고(infra/ecs.tf), 여기서 그걸 읽는다.
     */
    @Bean
    S3Client s3Client(@org.springframework.beans.factory.annotation.Value(
            "${ondo.storage.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
