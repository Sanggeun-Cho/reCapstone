package com.back.file;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 파일 저장소 설정 (정책 제8장, DESIGN_REVIEW.md 6.8). 값을 비워 두면 정책에 정한 기본값을 쓴다.
 * 비밀값(접근 키)은 환경변수로만 주입한다.
 *
 * @param endpoint         S3 호환 저장소 주소. 비우면 AWS S3 기본 주소
 * @param region           리전
 * @param bucket           버킷 이름
 * @param accessKey        접근 키. 비우면 AWS 기본 자격 증명 체인
 * @param secretKey        비밀 키
 * @param pathStyle        버킷을 경로에 넣는 주소 형식 (로컬 저장소용)
 * @param autoCreateBucket 시작할 때 버킷이 없으면 만들고 CORS를 설정한다 (로컬·테스트 전용)
 * @param corsOrigins      버킷 CORS에서 GET을 허용할 프론트엔드 주소 ({@code autoCreateBucket}일 때)
 * @param presignTtl       내려받기 주소 유효 기간 (정책 8.1 제2항)
 * @param maxFileSize      파일 1개 크기 상한 (정책 8.2 제1항)
 * @param maxRequestSize   요청 전체 크기 상한
 * @param maxFiles         요청당 파일 수 상한
 * @param trashRetention   휴지통 보관 기간 (정책 8.5 제5항)
 * @param orphanGrace      DB 기록이 없는 객체를 지우기 전에 기다리는 시간. 업로드가 끝나기 전에 지우지 않을 만큼 둔다
 */
@ConfigurationProperties("app.storage")
public record FileProperties(String endpoint, String region, String bucket, String accessKey, String secretKey,
        boolean pathStyle, boolean autoCreateBucket, List<String> corsOrigins, Duration presignTtl,
        DataSize maxFileSize, DataSize maxRequestSize, Integer maxFiles, Duration trashRetention,
        Duration orphanGrace) {

    public FileProperties {
        region = isBlank(region) ? "us-east-1" : region;
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
        presignTtl = presignTtl == null ? Duration.ofMinutes(5) : presignTtl;
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(200) : maxFileSize;
        maxRequestSize = maxRequestSize == null ? DataSize.ofMegabytes(500) : maxRequestSize;
        maxFiles = maxFiles == null ? 20 : maxFiles;
        trashRetention = trashRetention == null ? Duration.ofDays(30) : trashRetention;
        orphanGrace = orphanGrace == null ? Duration.ofHours(1) : orphanGrace;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
