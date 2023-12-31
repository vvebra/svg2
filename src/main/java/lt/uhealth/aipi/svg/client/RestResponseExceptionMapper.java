package lt.uhealth.aipi.svg.client;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lt.uhealth.aipi.svg.exception.RestApiException;
import lt.uhealth.aipi.svg.util.ExceptionMapper;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

@Provider
public class RestResponseExceptionMapper implements ResponseExceptionMapper<RestApiException> {
    @Override
    public RestApiException toThrowable(Response response) {
        return ExceptionMapper.fromResponse(response);
    }
}
