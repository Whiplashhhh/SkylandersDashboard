package net.vanbaelinghem.skylanders.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ToySnapshotRepository extends JpaRepository<ToySnapshot, Long> {

    Optional<ToySnapshot> findFirstByToyOrderByCapturedAtDesc(Toy toy);

    List<ToySnapshot> findByToyOrderByCapturedAtAsc(Toy toy);

    boolean existsByToyAndContentHash(Toy toy, byte[] contentHash);

    /**
     * The most recent snapshot of every figurine, in one query. Walking toy by toy would issue 702
     * round trips for a page that is read on every visit to the statistics screen.
     */
    @Query("select s from ToySnapshot s where s.capturedAt = "
            + "(select max(s2.capturedAt) from ToySnapshot s2 where s2.toy = s.toy)")
    List<ToySnapshot> findLatestPerToy();
}
