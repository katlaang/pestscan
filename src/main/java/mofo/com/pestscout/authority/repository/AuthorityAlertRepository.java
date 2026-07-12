package mofo.com.pestscout.authority.repository;

import mofo.com.pestscout.authority.model.AuthorityAlert;
import mofo.com.pestscout.authority.model.AuthorityAlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthorityAlertRepository extends JpaRepository<AuthorityAlert, UUID> {

    List<AuthorityAlert> findByDeletedFalse();

    List<AuthorityAlert> findByDeletedFalseAndActiveTrue();

    List<AuthorityAlert> findByDeletedFalseAndActiveTrueAndSeverity(AuthorityAlertSeverity severity);
}
