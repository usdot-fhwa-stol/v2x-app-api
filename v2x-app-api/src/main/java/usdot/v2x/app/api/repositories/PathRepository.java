package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.Path;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PathRepository extends JpaRepository<Path, Long> {

    /**
     * Find all active paths
     */
    @Query("SELECT p FROM Path p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    List<Path> findAllActive();

    /**
     * Find path by ID and active status
     */
    @Query("SELECT p FROM Path p WHERE p.id = :id AND p.isActive = true")
    Optional<Path> findByIdAndActive(@Param("id") Long id);

    /**
     * Find paths by name (case-insensitive)
     */
    @Query("SELECT p FROM Path p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true ORDER BY p.createdAt DESC")
    List<Path> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find paths by created by user
     */
    @Query("SELECT p FROM Path p WHERE p.createdBy = :createdBy AND p.isActive = true ORDER BY p.createdAt DESC")
    List<Path> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Check if path exists by name
     */
    @Query("SELECT COUNT(p) > 0 FROM Path p WHERE p.name = :name AND p.isActive = true")
    boolean existsByName(@Param("name") String name);

    /**
     * Find path by name and active status
     */
    @Query("SELECT p FROM Path p WHERE p.name = :name AND p.isActive = true")
    Optional<Path> findByNameAndActive(@Param("name") String name);

    /**
     * Find inactive paths (for re-activation)
     */
    @Query("SELECT p FROM Path p WHERE p.isActive = false ORDER BY p.createdAt DESC")
    List<Path> findAllInactive();
}
