package lt.uhealth.aipi.svg.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lt.uhealth.aipi.svg.client.AipiCoClient;
import lt.uhealth.aipi.svg.exception.AppRuntimeException;
import lt.uhealth.aipi.svg.exception.RestApiException;
import lt.uhealth.aipi.svg.model.MagicItemWithNotes;
import lt.uhealth.aipi.svg.model.Payload;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class AipiCoService {

    private static final Logger LOG = LoggerFactory.getLogger(AipiCoService.class);

    private final AipiCoClient aipiCoClient;

    @Inject
    public AipiCoService(@RestClient AipiCoClient aipiCoClient){
        this.aipiCoClient = aipiCoClient;
    }

    public Uni<List<MagicItemWithNotes>> getMagic(String magic){
        return Uni.createFrom().item(magic)
                .invoke(m -> LOG.debug("Requesting getMagic with {}", m))
                .flatMap(aipiCoClient::getMagic)
                .onFailure().invoke(t -> LOG.error("Error while getMagic {}: {}: {}",
                        magic, t.getClass(), t.getMessage()))
                .onFailure().retry().withBackOff(Duration.ofMillis(200)).atMost(3)
                .map(l -> toMagicItemsWithNotes(magic, l))
                .invoke(rez -> LOG.debug("getMagic result size is {}", rez.size()));
    }

    public Multi<MagicItemWithNotes> solveMagicItems(List<MagicItemWithNotes> magicItemWithNotes){
        return Uni.createFrom().item(magicItemWithNotes)
                .map(this::enrichMagicItems)
                .map(this::findIndependentMagicItems)
                .invoke(l -> LOG.debug("Independent magicItems: {}",
                        l.stream().map(m -> m.magicItem().index()).toList()))
                .onItem().transformToMulti(l -> Multi.createFrom().iterable(l))
                .flatMap(this::solveMagicItemWithDependents)
                .onCompletion().invoke(() -> LOG.debug("solveMagicItemsWithDependents completed"))
                .onCompletion().continueWith(magicItemWithNotes);
    }

    Multi<MagicItemWithNotes> solveMagicItemWithDependents(MagicItemWithNotes magicItemWithNotes){
        LOG.debug("solveMagicItemWithDependents for MagicItem: {}", magicItemWithNotes.magicItem().index());
        return postMagic(magicItemWithNotes)
                .map(MagicItemWithNotes::findReadyDependents)
                .map(l -> l.stream()
                        .filter(m -> !m.pickedForRequest().getAndSet(true))
                        .toList())
                .invoke(l -> LOG.debug("Picked ready for request dependents of MagicItem: {}: {}",
                        magicItemWithNotes.magicItem().index(),
                        l.stream().map(m -> m.magicItem().index()).toList()))
                .onItem().transformToMulti(l -> Multi.createFrom().iterable(l))
                .flatMap(this::solveMagicItemWithDependents);
    }

    Uni<MagicItemWithNotes> postMagic(MagicItemWithNotes magicItemWithNotes){
        return postMagic(magicItemWithNotes, null);
    }

    Uni<MagicItemWithNotes> postMagic(MagicItemWithNotes magicItemWithNotes, Throwable prevThrowable){
        Uni<MagicItemWithNotes> uni = Uni.createFrom().item(magicItemWithNotes);
        if (prevThrowable != null){
            if (prevThrowable instanceof RestApiException rae && rae.isTooEarly()){
                long tooEarlyByMillis = rae.tooEarlyByMillis();
                uni = uni
                        .invoke(m -> LOG.debug("Delaying postMagic for magicItem {}: {} ms",
                                m.magicItem().index(), tooEarlyByMillis))
                        .onItem().delayIt().by(Duration.ofMillis(tooEarlyByMillis));
            } else {
                return Uni.createFrom().failure(prevThrowable);
            }
        }

        Uni<MagicItemWithNotes> uni2 = Uni.createFrom().item(magicItemWithNotes)
                .invoke(m -> LOG.debug("Requesting postMagic for magicItem: {}", m.magicItem().index()))
                .flatMap(m -> aipiCoClient.postMagic(m.magic(), Payload.fromMagicItemWithNotes(m)))
                .onFailure().invoke(t -> LOG.error("Error while postMagic for magicItem {}: {}: {}",
                        magicItemWithNotes.magicItem().index(), t.getClass(), t.getMessage()))
                .onFailure(t -> !tooEarlyOrTooLate(t)).retry()
                        .withBackOff(Duration.ofMillis(10)).atMost(3)
                .map(magicItemWithNotes::withAnswer)
                .invoke(m -> LOG.debug("Answer from postMagic for magicItem {}: {}", m.magicItem().index(), m.answer()))
                .onFailure(ignored -> prevThrowable == null).recoverWithUni(t -> postMagic(magicItemWithNotes, t));

        return uni.flatMap(ignored -> uni2);
    }

    boolean tooEarlyOrTooLate(Throwable t){
        return (t instanceof RestApiException rae) && (rae.isTooEarly() || rae.isTooLate());
    }

    List<MagicItemWithNotes> enrichMagicItems(List<MagicItemWithNotes> magicItemsWithNotes) {
        Map<Integer, MagicItemWithNotes> magicItemsWithNotesMap = magicItemsWithNotes.stream()
                .map(m -> m.withDependents(new HashMap<>()))
                .collect(Collectors.toMap(m -> m.magicItem().index(), Function.identity()));

        magicItemsWithNotesMap.values().forEach(m -> {
            Set<Integer> dependencies = m.getDependencies();
            dependencies.forEach(d -> magicItemsWithNotesMap.get(d).dependents().get().put(m.magicItem().index(), m));
        });

        magicItemsWithNotesMap.values().forEach(MagicItemWithNotes::withImmutableDependents);

        if (magicItemsWithNotesMap.size() != magicItemsWithNotes.size()) {
            throw new AppRuntimeException(
                    "Size of magicItemsWithNotesMap %s is not equals to the size of magicItemsWithNotes %s"
                            .formatted(magicItemsWithNotesMap.size(), magicItemsWithNotes.size()));
        }

        magicItemsWithNotes.forEach(m -> m.withDependsOn(magicItemsWithNotesMap));

        return magicItemsWithNotes;
    }

    List<MagicItemWithNotes> findIndependentMagicItems(List<MagicItemWithNotes> magicItems){
        return magicItems.stream()
                .filter(MagicItemWithNotes::isIndependent)
                .toList();
    }

    List<MagicItemWithNotes> toMagicItemsWithNotes(String magic, List<String> magicItemStrings){
        MagicItemWithNotes[] magicItemsWithNotes = new MagicItemWithNotes[magicItemStrings.size()];
        for (int i = 0; i < magicItemStrings.size(); i++) {
            magicItemsWithNotes[i] = MagicItemWithNotes.create(i, magic, magicItemStrings.get(i));
        }

        return List.of(magicItemsWithNotes);
    }
}
