package net.vanbaelinghem.skylanders.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrapContentRepository extends JpaRepository<TrapContent, Long> {

    Optional<TrapContent> findFirstByToyOrderByCapturedAtDesc(Toy toy);
}
