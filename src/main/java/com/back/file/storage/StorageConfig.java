package com.back.file.storage;

import com.back.file.FileProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 저장소 클라이언트. {@code app.storage.endpoint}가 비어 있으면 AWS S3를, 있으면 그 S3 호환 저장소를 쓴다.
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(FileProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(properties.pathStyle());
        if (!FileProperties.isBlank(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(FileProperties properties) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
        if (!FileProperties.isBlank(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean
    S3FileStorage fileStorage(S3Client s3Client, S3Presigner s3Presigner, FileProperties properties) {
        S3FileStorage storage = new S3FileStorage(s3Client, s3Presigner, properties.bucket());
        if (properties.autoCreateBucket()) {
            storage.createBucketIfMissing(properties.corsOrigins());
        }
        return storage;
    }

    private static AwsCredentialsProvider credentials(FileProperties properties) {
        if (FileProperties.isBlank(properties.accessKey())) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
