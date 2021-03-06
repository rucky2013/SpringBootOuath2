/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package su90.springbootouath2;


import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.ErrorPage;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.filter.OAuth2ClientAuthenticationProcessingFilter;
import org.springframework.security.oauth2.client.filter.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CompositeFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;
/**
 *
 * @author superman90
 */
@SpringBootApplication
@Controller
@EnableOAuth2Client
@EnableAuthorizationServer
@Order(6)
public class SocialApplication extends WebSecurityConfigurerAdapter {
	
	@Autowired
	OAuth2ClientContext oauth2ClientContext;

	@RequestMapping({ "/user", "/me" })
	public Map<String, String> user(Principal principal) {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("name", principal.getName());
		return map;
	}
        
        @RequestMapping({ "/principal" })
	public Principal preincipal(Principal principal) {
		return principal;
	}
        
        @RequestMapping("/session")
	public HashMap<String,String> session(HttpServletRequest request) {
            HttpSession mysession =  request.getSession();
            
            HashMap<String,String> result = new HashMap();
            Enumeration<String> iterator = mysession.getAttributeNames();
            
            while(iterator.hasMoreElements()){
                String name = iterator.nextElement();
                result.put(name,mysession.getAttribute(name).toString());
            }
                        
            return result;            
	}
        
        @RequestMapping("/unauthenticated")
        public String unauthenticated() {
          return "redirect:/?error=true";
        }

	@Override
	protected void configure(HttpSecurity http) throws Exception {
//		 @formatter:off	
		http.antMatcher("/**")
			.authorizeRequests()
				.antMatchers("/", "/login**", "/webjars/**","/session").permitAll()
                            .anyRequest().authenticated()
                            .and().exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/"))
                            .and().logout().logoutSuccessUrl("/").permitAll()
                            .and().csrf().csrfTokenRepository(csrfTokenRepository())
                            .and().addFilterAfter(csrfHeaderFilter(), CsrfFilter.class)
                            .addFilterBefore(ssoFilter(), BasicAuthenticationFilter.class);
		// @formatter:on
	}
        
        @Configuration
	@EnableResourceServer
	protected static class ResourceServerConfiguration
			extends ResourceServerConfigurerAdapter {
		@Override
		public void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.antMatcher("/me")
				.authorizeRequests().anyRequest().authenticated();
			// @formatter:on
		}
	}
        
        @Configuration
	protected static class ServletCustomizer {
		@Bean
		public EmbeddedServletContainerCustomizer customizer() {
			return container -> {
				container.addErrorPages(
						new ErrorPage(HttpStatus.UNAUTHORIZED, "/unauthenticated"));
			};
		}
	}


	public static void main(String[] args) {
		SpringApplication.run(SocialApplication.class, args);
	}
        
        //redirect usage
	@Bean
	public FilterRegistrationBean oauth2ClientFilterRegistration(
			OAuth2ClientContextFilter filter) {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(filter);
		registration.setOrder(-100);
		return registration;
	}
        
        @Bean
        @ConfigurationProperties("github")
        ClientResources github(){
            return new ClientResources();
        }
        
        @Bean
        @ConfigurationProperties("facebook")
        ClientResources facebook(){
            return new ClientResources();
        }

	private Filter ssoFilter() {
                CompositeFilter filter = new CompositeFilter();
                
                List<Filter> filters = new ArrayList<>();
                
                filters.add(ssoFilter( facebook(),"/login/facebook"));
                filters.add(ssoFilter( github(),"/login/github"));
                
                filter.setFilters(filters);
		return filter;
	}
        
        private Filter ssoFilter(ClientResources client, String path){
                OAuth2ClientAuthenticationProcessingFilter theFilter = new OAuth2ClientAuthenticationProcessingFilter(
				path);
		OAuth2RestTemplate theTemplate = new OAuth2RestTemplate(client.getClient(),
				oauth2ClientContext);
		theFilter.setRestTemplate(theTemplate);
		theFilter.setTokenServices(new UserInfoTokenServices(
				client.getResource().getUserInfoUri(), client.getClient().getClientId()));
		return theFilter;
        }

	private Filter csrfHeaderFilter() {
		return new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {
				CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
				if (csrf != null) {
					Cookie cookie = WebUtils.getCookie(request, "XSRF-TOKEN");
					String token = csrf.getToken();
					if (cookie == null || token != null && !token.equals(cookie.getValue())) {
						cookie = new Cookie("XSRF-TOKEN", token);
						cookie.setPath("/");
						response.addCookie(cookie);
					}
				}
				filterChain.doFilter(request, response);
			}
		};
	}

	private CsrfTokenRepository csrfTokenRepository() {
		HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
		repository.setHeaderName("X-XSRF-TOKEN");
		return repository;
	}

}

class ClientResources {
	private OAuth2ProtectedResourceDetails client = new AuthorizationCodeResourceDetails();
	private ResourceServerProperties resource = new ResourceServerProperties();

	public OAuth2ProtectedResourceDetails getClient() {
		return client;
	}

	public ResourceServerProperties getResource() {
		return resource;
	}
}