package net.vanbaelinghem.skylanders.domain;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRunRepository extends JpaRepository<ScanRun, Long> {

    List<ScanRun> findByOrderByStartedAtDesc(Limit limit);
}
