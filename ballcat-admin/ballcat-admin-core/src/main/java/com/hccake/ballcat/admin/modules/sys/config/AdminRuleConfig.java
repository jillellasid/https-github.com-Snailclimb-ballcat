package com.hccake.ballcat.admin.modules.sys.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author lingting 2020/6/24 21:46
 */
@Data
@ConfigurationProperties(prefix = "ballcat.admin.rule")
public class AdminRuleConfig {

	/**
	 * 指定id的用户为超级管理员
	 */
	private int id = -1;

	/**
	 * 指定 username 为超级管理员
	 */
	private String username;

}
