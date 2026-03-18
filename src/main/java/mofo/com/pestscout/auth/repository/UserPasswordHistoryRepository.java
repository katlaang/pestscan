package mofo.com.pestscout.auth.repository;

import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserPasswordHistoryRepository extends JpaRepository<UserPasswordHistory, UUID> {

    List<UserPasswordHistory> findByUserOrderByCreatedAtDescIdDesc(User user);
}
