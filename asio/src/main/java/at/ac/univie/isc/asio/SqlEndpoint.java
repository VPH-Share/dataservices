package at.ac.univie.isc.asio;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ListenableFuture;

public class SqlEndpoint implements QueryEndpoint {

	private final DatasetEngine engine;

	SqlEndpoint(final DatasetEngine engine) {
		super();
		this.engine = engine;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.ac.univie.isc.asio.QueryEndpoint#acceptUriQuery(java.lang.String)
	 */
	@Override
	public Response acceptUriQuery(final String query) {
		return process(query);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.univie.isc.asio.QueryEndpoint#acceptFormQuery(java.lang.String)
	 */
	@Override
	public Response acceptFormQuery(final String query) {
		return process(query);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.ac.univie.isc.asio.QueryEndpoint#acceptQuery(java.lang.String)
	 */
	@Override
	public Response acceptQuery(final String query) {
		return process(query);
	}

	// TODO clean up error handling
	private Response process(final String query) {
		try {
			final ListenableFuture<InputSupplier<InputStream>> result = engine
					.submit(query);
			try {
				final InputSupplier<InputStream> resultData = result.get();
				return Response.ok(resultData.getInput(),
						MediaType.APPLICATION_XML_TYPE).build();
			} catch (final ExecutionException e) {
				final Throwable cause = e.getCause();
				if (cause != null && cause instanceof Exception) {
					throw (Exception) cause;
				} else {
					throw e;
				}
			}
		} catch (final DatasetUsageException e) {
			throw new WebApplicationException(e, Response
					.status(Status.BAD_REQUEST).entity(e.getMessage()).build());
		} catch (final Exception e) {
			throw new WebApplicationException(e, Response.serverError()
					.entity(e.getMessage()).build());
		}
	}
}
