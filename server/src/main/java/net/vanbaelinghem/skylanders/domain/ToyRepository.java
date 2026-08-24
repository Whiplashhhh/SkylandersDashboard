package net.vanbaelinghem.skylanders.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToyRepository extends JpaRepository<Toy, Long> {

    Optional<Toy> findByFilePath(String filePath);

    List<Toy> findByToyIdAndVariantId(int toyId, int variantId);

    List<Toy> findByCategoryFolder(String categoryFolder);
}
