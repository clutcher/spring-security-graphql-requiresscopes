package com.abb.flow.schemasecurity.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@code flow-schema-security}.
 * <p>
 * Example application.yml:
 * <pre>
 * com:
 *   abb:
 *     flow:
 *       schema-security:
 *         user-management-url: ${url.user-management.v1}
 *         cache-enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "com.abb.flow.schema-security")
public class SchemaSecurityProperties {

    // TODO: define properties

}
