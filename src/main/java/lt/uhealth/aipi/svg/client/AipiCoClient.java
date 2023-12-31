package lt.uhealth.aipi.svg.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lt.uhealth.aipi.svg.model.Payload;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "aipico")
public interface AipiCoClient {

    @GET
    @Path("/{magic}")
    Uni<List<String>> getMagic(@PathParam("magic") String magic);

    @POST
    @Path("/{magic}")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> postMagic(@PathParam("magic") String magic, Payload magicValue);
}
