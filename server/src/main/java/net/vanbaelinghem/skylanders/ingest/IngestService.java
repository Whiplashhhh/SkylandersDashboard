package net.vanbaelinghem.skylanders.ingest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.classification.Classification;
import net.vanbaelinghem.skylanders.classification.Game;
import net.vanbaelinghem.skylanders.classification.PathClassifier;
import net.vanbaelinghem.skylanders.domain.Toy;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import net.vanbaelinghem.skylanders.domain.ToySnapshot;
import net.vanbaelinghem.skylanders.domain.ToySnapshotRepository;
import net.vanbaelinghem.skylanders.domain.TrapContentRepository;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
import net.vanbaelinghem.skylanders.format.DeltaCodec;
import net.vanbaelinghem.skylanders.format.SaveArea;
import net.vanbaelinghem.skylanders.format.SkyCrypto;
import net.vanbaelinghem.skylanders.format.ToyIdentity;
import net.vanbaelinghem.skylanders.format.ToyIdentityParser;
import net.vanbaelinghem.skylanders.format.save.ParseStatus;
import net.vanbaelinghem.skylanders.format.save.SaveProgress;
import net.vanbaelinghem.skylanders.format.save.TrapTeamSaveParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-side ingestion pipeline (SPEC.md §8.1). All decryption and parsing lives here. */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ToyRepository toys;
    private final ToySnapshotRepository snapshots;
    private final TrapContentRepository trapContents;
    private final VillainRepository villains;
    private final ToyIdentityParser identityParser;
    private final TrapTeamSaveParser trapTeamParser;
    private final PathClassifier classifier;
    private final IngestExclusions exclusions;

    public IngestService(ToyRepository toys, ToySnapshotRepository snapshots,
                         TrapContentRepository trapContents, VillainRepository villains,
                         ToyIdentityParser identityParser, TrapTeamSaveParser trapTeamParser,
                         PathClassifier classifier, IngestExclusions exclusions) {
        this.toys = toys;
        this.snapshots = snapshots;
        this.trapContents = trapContents;
        this.villains = villains;
        this.identityParser = identityParser;
        this.trapTeamParser = trapTeamParser;
        this.classifier = classifier;
        this.exclusions = exclusions;
    }

    @Transactional
    public IngestResult ingest(String relativePath, byte[] raw, String announcedSha256) {
        // 0 — un chemin exclu est accepte poliment mais rien n'est enregistre. Repondre une
        // erreur ferait echouer le scan de l'agent pour un fichier volontairement ignore.
        if (exclusions.isExcluded(relativePath)) {
            return IngestResult.excluded(relativePath);
        }

        // 1 — size (SPEC.md §8.1 step 1)
        if (raw.length != SkyCrypto.DUMP_SIZE) {
            throw reject(HttpStatus.BAD_REQUEST, relativePath,
                    "taille de " + raw.length + " octets, 1024 attendus");
        }

        // 2 — recompute the hash rather than trust the agent (defence in depth)
        byte[] hash = sha256(raw);
        String hex = HexFormat.of().formatHex(hash);
        if (announcedSha256 != null && !announcedSha256.equalsIgnoreCase(hex)) {
            throw reject(HttpStatus.BAD_REQUEST, relativePath,
                    "SHA-256 annonce " + announcedSha256 + " != calcule " + hex);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Classification classification = classifier.classify(relativePath);
        ToyIdentity identity = identityParser.parse(raw);

        Toy toy = toys.findByFilePath(relativePath).orElse(null);
        if (toy == null) {
            // The first file received for a path is stored whole and becomes the reference for
            // every later delta (FORMAT.md §7.3).
            toy = toys.save(new Toy(relativePath, identity.uid(),
                    identity.toyId(), identity.variantId(),
                    classification.game().name(), classification.element().label(),
                    classification.category().name(), now, raw));
            log.info("Nouvelle figurine {} (toy {} / variant {})",
                    relativePath, identity.toyId(), identity.variantId());
        } else {
            toy.touch(now);
            toy.refreshIdentity(identity.toyId(), identity.variantId(), identity.uid(),
                    classification.game().name(), classification.element().label(),
                    classification.category().name());
        }

        // 3 — idempotence: nothing to record when the content has not moved
        if (snapshots.existsByToyAndContentHash(toy, hash)) {
            return IngestResult.unchanged(relativePath);
        }

        byte[] decrypted = SkyCrypto.decrypt(raw);
        // Le controle d'ecriture partielle ne vaut que la ou le modele a deux zones miroir est
        // etabli, c'est-a-dire pour un jeu dote d'un parser. Applique aveuglement, il rejetait
        // 152 fichiers parfaitement sains (les consommables Imaginators, dont FORMAT.md §8.2
        // documente qu'ils ne suivent pas ce schema).
        if (trapTeamParser.supports(classification.game())) {
            rejectIfAmbiguousSaveArea(relativePath, raw, decrypted);
        }

        SaveProgress progress = parseProgress(classification, raw, decrypted);

        byte[] delta = DeltaCodec.encode(toy.getBaseline(), raw);
        ToySnapshot snapshot = snapshots.save(new ToySnapshot(
                toy, now, hash, delta,
                progress.xp(), progress.level(), progress.gold(), progress.upgradesBitfield(),
                progress.nickname(), progress.playtimeSeconds(),
                toOffset(progress.savedAt()),
                progress.parseStatus().name(), progress.hasPlayEvidence()));

        // The unlock date comes from the tag itself (+0x50), not from ingestion time: a figurine
        // played years ago is dated correctly on the very first scan (FORMAT.md §5.1).
        if (progress.hasPlayEvidence()) {
            OffsetDateTime unlocked = toOffset(progress.firstWrittenAt());
            toy.markPlayedAt(unlocked != null ? unlocked : now);
        }
        toy.setLastSavedAt(toOffset(progress.savedAt()));

        recordTrapContent(toy, classification, raw, decrypted, now);

        return IngestResult.stored(relativePath, snapshot.getId(),
                DeltaCodec.changedBlockCount(delta) + " bloc(s) modifie(s), statut "
                        + progress.parseStatus());
    }

    private SaveProgress parseProgress(Classification classification, byte[] raw, byte[] decrypted) {
        if (trapTeamParser.supports(classification.game())) {
            return trapTeamParser.parse(classification.category(), raw, decrypted);
        }
        // A game without a parser stays listed with its identity, without progress
        // (SPEC.md §11.1). "Never played" cannot be decided either — an honest unknown.
        return SaveProgress.unsupported(false);
    }

    private void recordTrapContent(Toy toy, Classification classification,
                                   byte[] raw, byte[] decrypted, OffsetDateTime now) {
        if (classification.category() != Category.TRAP
                || !trapTeamParser.supports(classification.game())) {
            return;
        }
        TrapTeamSaveParser.TrapContent content = trapTeamParser.parseTrap(raw, decrypted);
        Optional<net.vanbaelinghem.skylanders.domain.TrapContent> last =
                trapContents.findFirstByToyOrderByCapturedAtDesc(toy);
        if (last.isPresent() && last.get().getVillainRawId() == content.villainRawId()) {
            return; // unchanged, nothing worth a new row
        }
        trapContents.save(new net.vanbaelinghem.skylanders.domain.TrapContent(
                toy, now, content.villainRawId(), content.empty()));

        // Self-populating villain reference (SPEC.md §7.2): register the raw ID so the UI can
        // offer to name it.
        if (!content.empty() && !villains.existsById(content.villainRawId())) {
            villains.save(new Villain(content.villainRawId()));
            log.info("Nouveau vilain inconnu : raw ID {} (0x{})",
                    content.villainRawId(), Integer.toHexString(content.villainRawId()));
        }
    }

    /**
     * SPEC.md §8.2 — an identical sequence counter on both areas while their content differs means
     * we cannot tell which one is current. Reading the wrong one yields the <em>previous</em> save,
     * which no plausibility check would catch, so the file is refused and the previous snapshot
     * kept.
     */
    private void rejectIfAmbiguousSaveArea(String path, byte[] raw, byte[] decrypted) {
        // Appele uniquement pour un jeu dont la geometrie de sauvegarde est connue.
        if (SkyCrypto.isNeverPlayed(raw)) {
            return;
        }
        SaveArea a0 = SaveArea.of(decrypted, SaveArea.AREA_0);
        SaveArea a1 = SaveArea.of(decrypted, SaveArea.AREA_1);
        if (a0.sequenceCounter() == a1.sequenceCounter()
                && !Arrays.equals(a0.logical(), a1.logical())) {
            throw reject(HttpStatus.UNPROCESSABLE_ENTITY, path,
                    "compteur de sequence identique (" + a0.sequenceCounter()
                            + ") sur les deux zones alors que leur contenu differe — "
                            + "ecriture partielle probable (SPEC.md §8.2)");
        }
    }

    private IngestRejectedException reject(HttpStatus status, String path, String reason) {
        log.warn("Fichier rejete : {} — {}", path, reason);
        return new IngestRejectedException(status, reason);
    }

    private static OffsetDateTime toOffset(ZonedDateTime moment) {
        return moment == null ? null : moment.toOffsetDateTime();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 absent de la JVM", e);
        }
    }
}
