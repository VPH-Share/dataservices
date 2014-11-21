package at.ac.univie.isc.asio.security;

import at.ac.univie.isc.asio.Scope;
import com.google.common.base.Optional;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SuppressWarnings("UnusedDeclaration")
@WebFilter(filterName = "auth-filter", displayName = "Uri Auth Filter"
    , description = "Authorize based on the request URI"
    , urlPatterns = "/*"
    , dispatcherTypes = DispatcherType.REQUEST
    , asyncSupported = true
    , initParams = {})
public final class UriAuthFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(UriAuthFilter.class);

  private UriPermissionExtractor parser;
  private VphTokenExtractor extractor;

  /** servlet container constructor */
  public UriAuthFilter() {
    this(new UriPermissionExtractor(), new VphTokenExtractor());
  }

  public UriAuthFilter(final UriPermissionExtractor parser, final VphTokenExtractor extractor) {
    this.parser = parser;
    this.extractor = extractor;
  }

  @Override
  public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain chain) throws IOException, ServletException {
    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    try {
      authorize(request, response);
    } catch (UriPermissionExtractor.MalformedUri | VphTokenExtractor.MalformedAuthHeader | IllegalArgumentException error) {
      log.debug("reject request : {}", error.getMessage()); // TODO : log to access.log -> new marker
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, error.getMessage());
    }
  }

  private void authorize(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
    final UriPermissionExtractor.Result extracted =
        parser.accept(request.getRequestURI(), request.getContextPath());
    final Permission permission = Permission.parse(extracted.permission());
    final Token user = extractToken(request);
    log.debug("authorized {} with permission {} and redirecting to <{}>", user, permission, extracted.tail());
    final AuthorizedRequestProxy authorizedRequest =
        AuthorizedRequestProxy.wrap(request, user, permission);
    final RequestDispatcher dispatcher = request.getRequestDispatcher(extracted.tail());
    dispatcher.forward(authorizedRequest, response);
  }

  private Token extractToken(final HttpServletRequest request) {
    final Optional<String> authHeader =
        Optional.fromNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
    return extractor.authenticate(authHeader);
  }

  @Override
  public void init(final FilterConfig config) throws ServletException {
    final String contextPath = config.getServletContext().getContextPath();
    parser.cachePrefix(contextPath);
    log.info(Scope.SYSTEM.marker(), "initialized on context <{}>", contextPath);
  }

  @Override
  public void destroy() {
    log.info(Scope.SYSTEM.marker(), "shutting down");
  }

}