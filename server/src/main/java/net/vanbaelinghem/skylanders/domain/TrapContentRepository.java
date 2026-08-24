package net.vanbaelinghem.skylanders.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrapContentRepository extends JpaRepository<TrapContent, Long> {

    Optional<TrapContent> findFirstByToyOrderByCapturedAtDesc(Toy toy);

    /**
     * Current content of every trap, in one query. The ranking needs all of them at once, and
     * walking trap by trap would issue one round trip per row.
     */
    @Query("select c from TrapContent c where c.capturedAt = "
            + "(select max(c2.capturedAt) from TrapContent c2 where c2.toy = c.toy)")
    List<TrapContent> findLatestPerToy();
}
