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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                .invoke(rez -> LOG.debug("getMagic result strings are {}", rez))
                .map(l -> toMagicItemsWithNotes(magic, l))
                .invoke(rez -> LOG.debug("getMagic result size is {}", rez.size()));
    }

    public Multi<MagicItemWithNotes> solveMagicItems(List<MagicItemWithNotes> magicItemWithNotes){
        return Uni.createFrom().item(magicItemWithNotes)
                .map(this::enrichMagicItems)
                .map(this::updateUntil)
                .map(this::sortByUntil)
                .invoke(l -> LOG.debug("Independent magicItems: {}",
                        l.stream().map(m -> m.magicItem().index()).toList()))
                .onItem().transformToMulti(l -> Multi.createFrom().iterable(l))
                .flatMap(this::solveMagicItemWithDependents)
                .onCompletion().invoke(() -> LOG.debug("solveMagicItemsWithDependents completed"))
                .onCompletion().continueWith(magicItemWithNotes);
    }

    Multi<MagicItemWithNotes> solveMagicItemWithDependents(MagicItemWithNotes magicItemWithNotes){
        LOG.debug("solveMagicItemWithDependents for MagicItem: {} (until = {})",
                magicItemWithNotes.magicItem().index(), magicItemWithNotes.until().get());
        return postMagic(magicItemWithNotes)
                .map(MagicItemWithNotes::findReadyDependents)
                .map(l -> l.stream()
                        .filter(m -> !m.pickedForRequest().getAndSet(true))
                        .toList())
                .map(this::sortByUntil)
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
        String proof0 = null;
        if (prevThrowable != null){
            switch (prevThrowable) {
                case RestApiException rae when rae.isTooEarly() -> {
                    long tooEarlyByMillis = rae.tooEarlyByMillis();
                    uni = uni
                            .invoke(m -> LOG.debug("Delaying postMagic for magicItem {}: {} ms",
                                    m.magicItem().index(), tooEarlyByMillis))
                            .onItem().delayIt().by(Duration.ofMillis(tooEarlyByMillis));
                }
                case RestApiException rae when rae.isMissingDependencies() -> {
                    Set<Integer> dependencies = rae.getRestError().getMissingDependencies();
                    LOG.debug("Missing dependencies of magicItem: {}: {}",
                            magicItemWithNotes.magicItem().index(), dependencies);
                    Map<Integer, MagicItemWithNotes> allMagicItems = magicItemWithNotes.allMagicItems().get();
                    dependencies.forEach(d -> magicItemWithNotes.dependsOn().put(d, allMagicItems.get(d)));
                    dependencies.forEach(d -> allMagicItems.get(d).dependents()
                            .put(magicItemWithNotes.magicItem().index(), magicItemWithNotes));
                    magicItemWithNotes.pickedForRequest().set(false);

                    if (!magicItemWithNotes.isReady()
                            || magicItemWithNotes.pickedForRequest().getAndSet(true)) {
                        LOG.debug("magicItem: {} is ready: {} and not picked for request",
                                magicItemWithNotes.magicItem().index(), magicItemWithNotes.isReady());
                        return Uni.createFrom().item(magicItemWithNotes);
                    } else {
                        LOG.debug("magicItem: {} is ready and picked for request",
                                magicItemWithNotes.magicItem().index());
                    }
                }
                case RestApiException rae when rae.isMissingProof() -> {
                    proof0 = rae.getRestError().getProofMessage();
                    LOG.debug("proof of magicItem: {} is {}", magicItemWithNotes.magicItem().index(), proof0);
                }
                default -> {
                    return Uni.createFrom().failure(prevThrowable);
                }
            }
        }

        String proof = proof0;

        Uni<MagicItemWithNotes> uni2 = Uni.createFrom().item(magicItemWithNotes)
                .invoke(m -> LOG.debug("Requesting postMagic for magicItem: {}", m.magicItem().index()))
                .flatMap(m -> aipiCoClient.postMagic(m.magic(), Payload.fromMagicItemWithNotes(m, proof)))
                .onFailure().invoke(t -> LOG.error("Error while postMagic for magicItem {}: {}: {}",
                        magicItemWithNotes.magicItem().index(), t.getClass(), t.getMessage()))
                .onFailure(t -> !tooEarlyOrTooLateOrMissingProofOrMissingDeps(t)).retry().atMost(3)
                .map(magicItemWithNotes::withAnswer)
                .invoke(m -> LOG.debug("Answer from postMagic for magicItem {}: {}", m.magicItem().index(), m.answer()))
                .onFailure().recoverWithUni(t -> postMagic(magicItemWithNotes, t));

        return uni.flatMap(ignored -> uni2);
    }

    boolean tooEarlyOrTooLateOrMissingProofOrMissingDeps(Throwable t){
        return (t instanceof RestApiException rae) && (rae.isTooEarly() || rae.isTooLate()
                || rae.isMissingProof() || rae.isMissingDependencies());
    }

    List<MagicItemWithNotes> enrichMagicItems(List<MagicItemWithNotes> magicItemsWithNotes) {
        Map<Integer, MagicItemWithNotes> magicItemsWithNotesMap = magicItemsWithNotes.stream()
                .collect(Collectors.toUnmodifiableMap(m -> m.magicItem().index(), Function.identity()));

        if (magicItemsWithNotesMap.size() != magicItemsWithNotes.size()) {
            throw new AppRuntimeException(
                    "Size of magicItemsWithNotesMap %s is not equals to the size of magicItemsWithNotes %s"
                            .formatted(magicItemsWithNotesMap.size(), magicItemsWithNotes.size()));
        }

        magicItemsWithNotes.forEach(m -> m.allMagicItems().set(magicItemsWithNotesMap));

        return magicItemsWithNotes;
    }

    List<MagicItemWithNotes> updateUntil(List<MagicItemWithNotes> magicItems){
        magicItems.forEach(MagicItemWithNotes::updateUntilRecursively);
        return magicItems;
    }

    List<MagicItemWithNotes> sortByUntil(List<MagicItemWithNotes> magicItems){
        return magicItems.stream()
                .sorted(Comparator.nullsLast(Comparator.comparing(m -> m.until().get())))
                .toList();
    }

    List<MagicItemWithNotes> toMagicItemsWithNotes(String magic, List<String> magicItemStrings){
        return Stream.iterate(0, i -> i + 1)
                .limit(magicItemStrings.size())
                .map(i -> MagicItemWithNotes.create(i, magic, magicItemStrings.get(i)))
                .toList();
    }
}
