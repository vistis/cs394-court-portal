package kh.edu.paragoniu.court_portal.config;

import kh.edu.paragoniu.court_shared.config.S3Config;
import kh.edu.paragoniu.court_shared.service.S3Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnExpression(
    "!'${spring.storage.s3.access-key:}'.isBlank() && " +
    "!'${spring.storage.s3.secret-key:}'.isBlank() && " +
    "!'${spring.storage.s3.endpoint:}'.isBlank() && " +
    "!'${spring.storage.s3.bucket-name:}'.isBlank()"
)
@Import({ S3Config.class, S3Service.class })
public class StorageConfig {}
