package lt.uhealth.aipi.svg.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class ExceptionHandler implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    @Override
    public Response toResponse(Throwable throwable) {
        LOG.error("Handling exception from controller", throwable);

        return Response.serverError().build();
    }
}
