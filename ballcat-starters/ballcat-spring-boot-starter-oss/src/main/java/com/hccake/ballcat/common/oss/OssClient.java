package com.hccake.ballcat.common.oss;

import com.hccake.ballcat.common.oss.domain.StreamTemp;
import com.hccake.ballcat.common.oss.exception.OssDisabledException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * @author lingting 2021/5/11 9:59
 */
@Getter
public class OssClient implements DisposableBean {

	private final String endpoint;

	private final String region;

	private final String accessKey;

	private final String accessSecret;

	private final String bucket;

	private final String domain;

	private final String root;

	private final S3Client client;

	private final ObjectCannedACL acl;

	private String downloadPrefix;

	private boolean enable = true;

	public OssClient(OssProperties properties) {
		this.endpoint = properties.getEndpoint();
		this.region = properties.getRegion();
		this.accessKey = properties.getAccessKey();
		this.accessSecret = properties.getAccessSecret();
		this.bucket = properties.getBucket();
		this.domain = properties.getDomain();
		this.root = properties.getRootPath();
		this.acl = properties.getAcl();

		final boolean isEnable = !StringUtils.hasText(accessKey) || !StringUtils.hasText(accessSecret)
				|| (!StringUtils.hasText(endpoint) && !StringUtils.hasText(domain));
		if (isEnable) {
			this.enable = false;
			client = null;
		}
		else {
			final ClientBuilder builder = createBuilder();
			client = builder.build();
			downloadPrefix = builder.downloadPrefix();
			// ?????? / ??????
			if (downloadPrefix.endsWith(OssConstants.SLASH)) {
				downloadPrefix = downloadPrefix.substring(0, downloadPrefix.length() - 1);
			}
		}
	}

	/**
	 * ?????? builder . ??????????????????
	 * @author lingting 2021-05-13 14:43
	 */
	protected ClientBuilder createBuilder() {
		return ClientBuilder.builder().accessKey(accessKey).accessSecret(accessSecret).bucket(bucket).domain(domain)
				.endpoint(endpoint).region(region);
	}

	/**
	 * ????????????, ????????????????????????, ???????????????, ???????????? upload(stream, relativePath, size) ??????
	 * @param relativePath ???????????? getRoot() ?????????
	 * @param stream ???????????????
	 * @return ??????????????????
	 * @throws IOException ??????????????????
	 */
	public String upload(InputStream stream, String relativePath) throws IOException {
		final StreamTemp temp = getSize(stream);
		return upload(temp.getStream(), relativePath, temp.getSize());
	}

	public String upload(InputStream stream, String relativePath, Long size) {
		return upload(stream, relativePath, size, acl);
	}

	public String upload(InputStream stream, String relativePath, Long size, ObjectCannedACL acl) {
		final String path = getPath(relativePath);
		final PutObjectRequest.Builder builder = PutObjectRequest.builder().bucket(bucket).key(path);

		if (acl != null) {
			// ????????????
			builder.acl(acl);
		}

		getClient().putObject(builder.build(), RequestBody.fromInputStream(stream, size));
		return path;
	}

	public void delete(String path) {
		getClient().deleteObject(builder -> builder.bucket(bucket).key(getPath(path)));
	}

	@SneakyThrows
	public void copy(String absoluteSource, String absoluteTarget) {
		String s = getCopyUrl(absoluteSource);
		final CopyObjectRequest request = CopyObjectRequest.builder().copySource(s).destinationBucket(bucket)
				.destinationKey(getPath(absoluteTarget)).build();
		getClient().copyObject(request);
	}

	/**
	 * ?????? ???????????? ?????????url
	 * @author lingting 2021-05-12 18:50
	 */
	public String getDownloadUrl(String relativePath) {
		return getDownloadUrlByAbsolute(getPath(relativePath));
	}

	/**
	 * ?????? ???????????? ?????????url
	 * @author lingting 2021-05-12 18:50
	 */
	public String getDownloadUrlByAbsolute(String path) {
		return String.format("%s/%s", downloadPrefix, path);
	}

	protected String getCopyUrl(String path) throws UnsupportedEncodingException {
		return URLEncoder.encode(bucket + getPath(path), StandardCharsets.UTF_8.toString());
	}

	/**
	 * ??????oss????????????
	 * @author lingting 2021-05-27 10:45
	 */
	public boolean enabled() {
		return enable;
	}

	@SneakyThrows
	protected S3Client getClient() {
		if (client == null) {
			throw new OssDisabledException();
		}
		return client;
	}

	@Override
	public void destroy() throws Exception {
		if (client != null) {
			client.close();
		}
	}

	/**
	 * ????????????????????????????????????????????????????????????
	 * @param stream ?????????????????????
	 * @return StreamTemp ??????????????????????????????????????????
	 * @author lingting 2021-05-10 15:29
	 * @throws IOException ??????????????????
	 */
	public StreamTemp getSize(InputStream stream) throws IOException {
		return StreamTemp.of(stream);
	}

	/**
	 * ????????????????????????
	 * @param relativePath ???????????? getRoot() ?????????
	 * @return ??????????????????
	 * @author lingting 2021-05-10 15:58
	 */
	public String getPath(String relativePath) {
		Assert.hasText(relativePath, "path must not be empty");

		if (relativePath.startsWith(OssConstants.SLASH)) {
			relativePath = relativePath.substring(1);
		}

		return getRoot() + relativePath;
	}

}
