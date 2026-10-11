package app.Repo;

import app.models.User;
import app.models.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface UserRepo extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    List<User> findAllByOrderByIdAsc();

    List<User> findAllByRoleOrderByIdAsc(UserRole role);

    @Query("""
            select user from User user
            where (:role is null or user.role = :role)
              and (:query is null
                   or lower(user.name) like lower(concat('%', :query, '%'))
                   or lower(user.email) like lower(concat('%', :query, '%')))
            order by user.id asc
            """)
    Page<User> searchDirectory(@Param("role") UserRole role,
                               @Param("query") String query,
                               Pageable pageable);
}
