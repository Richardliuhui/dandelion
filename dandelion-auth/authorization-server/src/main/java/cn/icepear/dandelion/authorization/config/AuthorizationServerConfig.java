package cn.icepear.dandelion.authorization.config;

import cn.icepear.dandelion.common.security.component.DandelionUserAuthenticationConverter;
import cn.icepear.dandelion.common.security.component.error.DandelionAuthExceptionEntryPoint;
import cn.icepear.dandelion.common.security.component.error.DandelionOAuth2AccessDeniedHandler;
import cn.icepear.dandelion.common.security.component.error.DandelionWebResponseExceptionTranslator;
import cn.icepear.dandelion.common.security.constant.SecurityConstants;
import cn.icepear.dandelion.common.security.service.DandelionClientDetailsService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.security.oauth2.provider.approval.JdbcApprovalStore;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author rimwood
 * @date 2019/2/1
 * 认证服务器配置
 */
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {
	@Autowired
	private DataSource dataSource;
	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private UserDetailsService userDetailsService;
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	@Autowired
	private DandelionWebResponseExceptionTranslator dandelionWebResponseExceptionTranslator;
	@Autowired
	private DandelionAuthExceptionEntryPoint dandelionAuthExceptionEntryPoint;
	@Autowired
	private DandelionOAuth2AccessDeniedHandler dandelionOAuth2AccessDeniedHandler;


	@Bean
	public ClientDetailsService dandelionClientDetailsService(DataSource dataSource){
		DandelionClientDetailsService clientDetailsService = new DandelionClientDetailsService(dataSource);
		return clientDetailsService;
	}
	/**
	 * 自定义 oauth2 client 的实现
	 * @param clients
	 */
	@Override
	@SneakyThrows
	public void configure(ClientDetailsServiceConfigurer clients) {
		clients.withClientDetails(dandelionClientDetailsService(dataSource));
	}

	@Override
	public void configure(AuthorizationServerSecurityConfigurer oauthServer) {

		oauthServer
			// AuthenticationException指的是未登录状态下访问受保护资源,如果异常是 AuthenticationException，使用 AuthenticationEntryPoint 处理
			.authenticationEntryPoint(dandelionAuthExceptionEntryPoint)

			// AccessDeniedException指的是登陆了但是由于权限不足,
			// 如果异常是 AccessDeniedException 且用户是匿名用户，使用 AuthenticationEntryPoint 处理
			// 如果异常是 AccessDeniedException 且用户不是匿名用户，交给 AccessDeniedHandler 处理
			.accessDeniedHandler(dandelionOAuth2AccessDeniedHandler)
			// 允许表单认证
			// ClientCredentialsTokenEndpointFilter是Oauth2 Token Endpoint的认证端口，如果使用了这条安全过滤器，就会通过请求参数去对客户端进行认证。
			// 规范中是允许的[但不推荐]，而更倾向推荐使用HTTP basic认证，一旦使用HTTP basic认证之后，就不需要使用这个过滤器了
			//.allowFormAuthenticationForClients()
			.passwordEncoder(passwordEncoder())
			// 都能访问 /oauth/token_key：提供公有密匙的端点，如果你使用JWT令牌的话。
			.tokenKeyAccess("permitAll()")
			// 需要授权才能访问 /oauth/check_token：用于资源服务访问的令牌解析端点。
			.checkTokenAccess("isAuthenticated()");
	}

	@Override
	public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
		DefaultAccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter();
		UserAuthenticationConverter userTokenConverter = new DandelionUserAuthenticationConverter();
		accessTokenConverter.setUserTokenConverter(userTokenConverter);

		endpoints
			.allowedTokenEndpointRequestMethods(HttpMethod.GET, HttpMethod.POST)
			.tokenStore(tokenStore())
			.approvalStore(approvalStore())
			.tokenEnhancer(tokenEnhancer())
			.userDetailsService(userDetailsService)
			.accessTokenConverter(accessTokenConverter)
			.authenticationManager(authenticationManager)
			.reuseRefreshTokens(false)
			.exceptionTranslator(dandelionWebResponseExceptionTranslator);
	}


	/**
	 * 存储授权store
	 *
	 * @return
	 */
	@Bean
	public ApprovalStore approvalStore() {
		return new JdbcApprovalStore(dataSource);
	}

	/**
	 * 存储token
	 * @return
	 */
	@Bean
	public TokenStore tokenStore() {
		RedisTokenStore tokenStore = new RedisTokenStore(redisConnectionFactory);
		tokenStore.setPrefix(SecurityConstants.PROJECT_PREFIX + SecurityConstants.OAUTH_PREFIX);
		return tokenStore;
	}

	@Bean
	public TokenEnhancer tokenEnhancer() {
		return (accessToken, authentication) -> {
			final Map<String, Object> additionalInfo = new HashMap<>(1);
			additionalInfo.put("license", SecurityConstants.PROJECT_LICENSE);
			((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInfo);
			return accessToken;
		};
	}

	/**
	 * https://spring.io/blog/2017/11/01/spring-security-5-0-0-rc1-released#password-storage-updated
	 * Encoded password does not look like BCrypt
	 *
	 * @return PasswordEncoder
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
