package com.hccake.ballcat.common.oss;

import com.hccake.ballcat.common.oss.interceptor.ModifyPathInterceptor;
import java.net.URI;
import java.util.function.Consumer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * @author lingting 2021/5/12 22:01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClientBuilder {

	private String endpoint;

	private String regionStr;

	private String accessKey;

	private String accessSecret;

	private String bucket;

	private String domain;

	private String downloadPrefix;

	public static ClientBuilder builder() {
		return new ClientBuilder();
	}

	public String endpoint() {
		return endpoint;
	}

	public ClientBuilder endpoint(String endpoint) {
		this.endpoint = endpoint;
		return this;
	}

	public String region() {
		return regionStr;
	}

	public ClientBuilder region(String region) {
		this.regionStr = region;
		return this;
	}

	public String accessKey() {
		return accessKey;
	}

	public ClientBuilder accessKey(String accessKey) {
		this.accessKey = accessKey;
		return this;
	}

	public String accessSecret() {
		return accessSecret;
	}

	public ClientBuilder accessSecret(String accessSecret) {
		this.accessSecret = accessSecret;
		return this;
	}

	public String bucket() {
		return bucket;
	}

	public ClientBuilder bucket(String bucket) {
		this.bucket = bucket;
		return this;
	}

	public String downloadPrefix() {
		return downloadPrefix;
	}

	public ClientBuilder downloadPrefix(String downloadPrefix) {
		this.downloadPrefix = downloadPrefix;
		return this;
	}

	public String domain() {
		return domain;
	}

	public ClientBuilder domain(String domain) {
		this.domain = domain;
		return this;
	}

	@SneakyThrows
	private S3ClientBuilder create() {
		S3ClientBuilder builder = S3Client.builder();

		// ??????????????????
		builder.serviceConfiguration(sb -> sb.pathStyleAccessEnabled(false).chunkedEncodingEnabled(false));

		String uriStr = domain;

		// ????????????????????????
		if (!StringUtils.hasText(uriStr)) {
			uriStr = endpoint;

			// ???????????????
			if (endpoint.contains(OssConstants.AWS_INTERNATIONAL)
					// ??????s3??????
					&& !endpoint.startsWith(OssConstants.S3)) {
				uriStr = OssConstants.S3 + endpoint;
			}

			final Region region;
			if (StringUtils.hasText(regionStr)) {
				// ???????????????
				region = Region.of(regionStr);
			}
			else {
				// ?????????, ??????????????????
				regionStr = uriStr.startsWith(OssConstants.S3)
						// ?????????s3??????
						? uriStr.substring(OssConstants.S3.length(),
								uriStr.indexOf(OssConstants.DOT, OssConstants.S3.length() + 1))
						// ????????????
						: uriStr.substring(0, uriStr.indexOf(OssConstants.DOT));

				region = Region.of(regionStr);
			}

			builder.region(region);

			// ?????????????????? ????????????
			// https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/VirtualHosting.html
			uriStr = "https://" + bucket + "." + uriStr;
		}
		else {
			// ?????????????????????
			Assert.hasText(regionStr, "????????????????????????, ??????????????????!");
			builder.region(Region.of(regionStr));
		}

		// ????????????????????????
		downloadPrefix(uriStr);

		builder.endpointOverride(new URI(uriStr)).credentialsProvider(
				StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, accessSecret)));

		// ??????
		builder.overrideConfiguration(cb -> cb.addExecutionInterceptor(new ModifyPathInterceptor(bucket)));

		return builder;
	}

	public S3Client build() {
		return create().build();
	}

	/**
	 * ??????????????????
	 * @author lingting 2021-05-12 22:37
	 */
	public S3Client build(Consumer<S3ClientBuilder> consumer) {
		final S3ClientBuilder builder = create();
		consumer.accept(builder);
		return builder.build();
	}

}
