package lt.uhealth.aipi.svg.util;

import jakarta.ws.rs.core.Response;
import lt.uhealth.aipi.svg.model.RestError;
import lt.uhealth.aipi.svg.exception.RestApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ExceptionMapper {

    Logger LOG = LoggerFactory.getLogger(ExceptionMapper.class);

    static RestApiException fromResponse(Response response){
        String errorString = extractResponseText(response);
        RestError restError = parseError(errorString);
        return new RestApiException(response.getStatus(), errorString, restError);
    }

    static String extractResponseText(Response response){
        if (response == null){
            return null;
        }

        try {
            return response.readEntity(String.class);
        } catch (Exception e){
            LOG.warn("Error while response.readEntity(String.class)", e);
        }
        return null;
    }

    private static RestError parseError(String errorString){
        if (errorString == null || errorString.isEmpty()){
            return null;
        }

        try {
            return RestError.fromErrorString(errorString);
        } catch (RuntimeException re){
            LOG.error("Error while parsing RestError", re);
            return null;
        }
    }
}
