package lt.uhealth.aipi.svg;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import lt.uhealth.aipi.svg.model.MagicItemWithNotes;
import lt.uhealth.aipi.svg.service.AipiCoService;
import lt.uhealth.aipi.svg.util.Base64Decoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

@ApplicationScoped
@Path("/{magic}")
public class SvgController {

    private static final Logger LOG = LoggerFactory.getLogger(SvgController.class);

    private final AipiCoService aipiCoService;

    @Inject
    public SvgController(AipiCoService aipiCoService){
        this.aipiCoService = aipiCoService;
    }

    @GET
    @Produces("image/svg")
    public Uni<String> getMagic(@PathParam("magic") String magic){
        LOG.info("getMagic(magic={}) invoked", magic);

        return aipiCoService.getMagic(magic)
                .toMulti()
                .flatMap(aipiCoService::processMagicItems)
                .collect().asList()
                .map(l -> l.stream()
                        .sorted(Comparator.comparingInt(MagicItemWithNotes::index))
                        .map(m -> m.answer().get().payload())
                        .reduce("", String::concat))
                .map(Base64Decoder::decode);
    }
}
