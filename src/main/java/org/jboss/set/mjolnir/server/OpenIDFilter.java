package org.jboss.set.mjolnir.server;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.server.bean.UserRepository;
import org.jboss.set.mjolnir.shared.domain.RegisteredUser;
import org.keycloak.KeycloakSecurityContext;

import javax.ejb.EJB;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class OpenIDFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(SamlFilter.class.getName());

    @EJB
    private UserRepository userRepository;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        KeycloakSecurityContext ctx = (KeycloakSecurityContext) servletRequest.getAttribute(KeycloakSecurityContext.class.getName());

        if (ctx != null) {
            RegisteredUser oldUser = (RegisteredUser) request.getSession(true)
                    .getAttribute(AuthenticationFilter.AUTHENTICATED_USER_SESSION_KEY);
            if (oldUser == null || !oldUser.isLoggedIn()) {
                String username = ctx.getToken().getPreferredUsername();
                RegisteredUser user = userRepository.getOrCreateUser(username);
                user.setLoggedIn(true);
                request.getSession(true).setAttribute(AuthenticationFilter.AUTHENTICATED_USER_SESSION_KEY, user);
            }
        } else {
            LOG.debugf("No SAML principal");
            request.getSession(true).removeAttribute(AuthenticationFilter.AUTHENTICATED_USER_SESSION_KEY);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
