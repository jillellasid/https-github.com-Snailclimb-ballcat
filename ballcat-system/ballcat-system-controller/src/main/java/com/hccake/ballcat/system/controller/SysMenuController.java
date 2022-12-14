package com.hccake.ballcat.system.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.hccake.ballcat.common.log.operation.annotation.CreateOperationLogging;
import com.hccake.ballcat.common.log.operation.annotation.DeleteOperationLogging;
import com.hccake.ballcat.common.log.operation.annotation.UpdateOperationLogging;
import com.hccake.ballcat.common.model.result.BaseResultCode;
import com.hccake.ballcat.common.model.result.R;
import com.hccake.ballcat.common.security.constant.UserAttributeNameConstants;
import com.hccake.ballcat.common.security.userdetails.User;
import com.hccake.ballcat.common.security.util.SecurityUtils;
import com.hccake.ballcat.system.converter.SysMenuConverter;
import com.hccake.ballcat.system.enums.SysMenuType;
import com.hccake.ballcat.system.model.dto.SysMenuCreateDTO;
import com.hccake.ballcat.system.model.dto.SysMenuUpdateDTO;
import com.hccake.ballcat.system.model.entity.SysMenu;
import com.hccake.ballcat.system.model.qo.SysMenuQO;
import com.hccake.ballcat.system.model.vo.SysMenuGrantVO;
import com.hccake.ballcat.system.model.vo.SysMenuPageVO;
import com.hccake.ballcat.system.model.vo.SysMenuRouterVO;
import com.hccake.ballcat.system.service.SysMenuService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ????????????
 *
 * @author hccake 2021-04-06 17:59:51
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/menu")
@Tag(name = "??????????????????")
public class SysMenuController {

	private final SysMenuService sysMenuService;

	/**
	 * ?????????????????????????????????
	 * @return ?????????????????????
	 */
	@GetMapping("/router")
	@Operation(summary = "????????????", description = "????????????")
	public R<List<SysMenuRouterVO>> getUserPermission() {
		// ????????????Code
		User user = SecurityUtils.getUser();
		Map<String, Object> attributes = user.getAttributes();

		Object rolesObject = attributes.get(UserAttributeNameConstants.ROLE_CODES);
		if (!(rolesObject instanceof Collection)) {
			return R.ok(new ArrayList<>());
		}

		@SuppressWarnings("unchecked")
		Collection<String> roleCodes = (Collection<String>) rolesObject;
		if (CollectionUtil.isEmpty(roleCodes)) {
			return R.ok(new ArrayList<>());
		}

		// ???????????????????????????
		Set<SysMenu> all = new HashSet<>();
		roleCodes.forEach(roleCode -> all.addAll(sysMenuService.listByRoleCode(roleCode)));

		// ???????????????
		List<SysMenuRouterVO> menuVOList = all.stream()
				.filter(menuVo -> SysMenuType.BUTTON.getValue() != menuVo.getType())
				.sorted(Comparator.comparingInt(SysMenu::getSort)).map(SysMenuConverter.INSTANCE::poToRouterVo)
				.collect(Collectors.toList());

		return R.ok(menuVOList);
	}

	/**
	 * ??????????????????
	 * @param sysMenuQO ????????????????????????
	 * @return R ???????????????
	 */
	@GetMapping("/list")
	@PreAuthorize("@per.hasPermission('system:menu:read')")
	@Operation(summary = "??????????????????", description = "??????????????????")
	public R<List<SysMenuPageVO>> getSysMenuPage(SysMenuQO sysMenuQO) {
		List<SysMenu> sysMenus = sysMenuService.listOrderBySort(sysMenuQO);
		if (CollectionUtil.isEmpty(sysMenus)) {
			R.ok(new ArrayList<>());
		}
		List<SysMenuPageVO> voList = sysMenus.stream().map(SysMenuConverter.INSTANCE::poToPageVo)
				.collect(Collectors.toList());
		return R.ok(voList);
	}

	/**
	 * ????????????????????????
	 * @return R ???????????????
	 */
	@GetMapping("/grant-list")
	@PreAuthorize("@per.hasPermission('system:menu:read')")
	@Operation(summary = "????????????????????????", description = "????????????????????????")
	public R<List<SysMenuGrantVO>> getSysMenuGrantList() {
		List<SysMenu> sysMenus = sysMenuService.list();
		if (CollectionUtil.isEmpty(sysMenus)) {
			R.ok(new ArrayList<>());
		}
		List<SysMenuGrantVO> voList = sysMenus.stream().map(SysMenuConverter.INSTANCE::poToGrantVo)
				.collect(Collectors.toList());
		return R.ok(voList);
	}

	/**
	 * ??????????????????
	 * @param sysMenuCreateDTO ????????????
	 * @return R ???????????????
	 */
	@CreateOperationLogging(msg = "??????????????????")
	@PostMapping
	@PreAuthorize("@per.hasPermission('system:menu:add')")
	@Operation(summary = "??????????????????", description = "??????????????????")
	public R<Void> save(@Valid @RequestBody SysMenuCreateDTO sysMenuCreateDTO) {
		return sysMenuService.create(sysMenuCreateDTO) ? R.ok()
				: R.failed(BaseResultCode.UPDATE_DATABASE_ERROR, "????????????????????????");
	}

	/**
	 * ??????????????????
	 * @param sysMenuUpdateDTO ??????????????????DTO
	 * @return R ???????????????
	 */
	@UpdateOperationLogging(msg = "??????????????????")
	@PutMapping
	@PreAuthorize("@per.hasPermission('system:menu:edit')")
	@Operation(summary = "??????????????????", description = "??????????????????")
	public R<Void> updateById(@RequestBody SysMenuUpdateDTO sysMenuUpdateDTO) {
		sysMenuService.update(sysMenuUpdateDTO);
		return R.ok();
	}

	/**
	 * ??????id??????????????????
	 * @param id id
	 * @return R ???????????????
	 */
	@DeleteOperationLogging(msg = "??????id??????????????????")
	@DeleteMapping("/{id}")
	@PreAuthorize("@per.hasPermission('system:menu:del')")
	@Operation(summary = "??????id??????????????????", description = "??????id??????????????????")
	public R<Void> removeById(@PathVariable("id") Integer id) {
		return sysMenuService.removeById(id) ? R.ok() : R.failed(BaseResultCode.UPDATE_DATABASE_ERROR, "??????id????????????????????????");
	}

}