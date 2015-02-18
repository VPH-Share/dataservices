package at.ac.univie.isc.asio.security;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class IsAuthorizedTest {
  @Rule
  public ExpectedException error = ExpectedException.none();

  private final SecurityContext security = Mockito.mock(SecurityContext.class);
  private final Request request = Mockito.mock(Request.class);
  private final IsAuthorized subject = IsAuthorized.given(security, request);

  @Before
  public void setUp() {
    when(request.getMethod()).thenReturn(HttpMethod.POST);
  }

  @Test
  public void should_be_satisfied_if_client_has_permission() throws Exception {
    when(security.isUserInRole(anyString())).thenReturn(true);
    assertThat(subject.apply(Permission.READ), is(true));
  }

  @Test
  public void should_not_be_satisfied_if_client_does_not_have_permission() throws Exception {
    when(security.isUserInRole(anyString())).thenReturn(false);
    assertThat(subject.apply(Permission.READ), is(false));
  }

  @Test
  public void should_not_be_satisfied_if_write_permission_required_on_GET_request() throws Exception {
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(security.isUserInRole(anyString())).thenReturn(true);
    assertThat(subject.apply(Permission.WRITE), is(false));
  }
}
