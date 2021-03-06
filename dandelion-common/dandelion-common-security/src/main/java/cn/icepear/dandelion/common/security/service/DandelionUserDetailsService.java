package cn.icepear.dandelion.common.security.service;

import cn.hutool.core.util.StrUtil;
import cn.icepear.dandelion.common.core.constant.CommonConstants;
import cn.icepear.dandelion.common.core.utils.R;
import cn.icepear.dandelion.common.core.utils.RedisUtils;
import cn.icepear.dandelion.common.security.constant.SecurityConstants;
import cn.icepear.dandelion.upm.api.domain.dto.UserInfo;
import cn.icepear.dandelion.upm.api.domain.entity.SysUser;
import cn.icepear.dandelion.upm.api.remote.RemoteUserService;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * @author rimwood
 * @date 2019-04-15
 * 用户详细信息
 */
@Slf4j
@Service("userDetailsService")
public class DandelionUserDetailsService implements UserDetailsService {
	@Autowired
	private RemoteUserService remoteUserService;
	@Autowired
	private RedisUtils redisUtils;

	/**
	 * 用户密码登录
	 *
	 * @param username 用户名
	 * @return
	 * @throws UsernameNotFoundException
	 */
	@Override
	@HystrixCommand(fallbackMethod = "userInfoError")
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		R<UserInfo> result = remoteUserService.info(username, SecurityConstants.FROM_IN);
		if(result.getCode()==0){
			DandelionUser userDetails = getUserDetails(result);
			return userDetails;
		}
		throw new UsernameNotFoundException(username);
	}

	public UserDetails userInfoError(String username) {
		throw new UsernameNotFoundException(username);
	}


	/**
	 * 构建userdetails
	 *
	 * @param result 用户信息
	 * @return
	 */
	private DandelionUser getUserDetails(R<UserInfo> result) {
		if (result == null || result.getData() == null) {
			throw new UsernameNotFoundException("用户不存在");
		}

		UserInfo info = result.getData();

		//权限标识集合
		Collection<? extends GrantedAuthority> authorities
				= AuthorityUtils.createAuthorityList(info.getPermissions().toArray(new String[0]));

		SysUser user = info.getSysUser();

		// 构造security用户
		return new DandelionUser(
				user.getId(),
				user.getDeptId(),
				user.getGrandparentDeptId(),
				user.getRealName(),
				user.getEmail(),
				user.getMobile(),
				user.getAvatar(),
				info.getRoles(),
				user.getUserName(),
				SecurityConstants.BCRYPT + user.getPassword(),
				StrUtil.equals(user.getDelFlag(), CommonConstants.STATUS_NORMAL),
				true,
				true,
				StrUtil.equals(user.getLockFlag(), CommonConstants.STATUS_NORMAL),
				authorities);
	}
}
