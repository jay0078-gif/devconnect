package com.devconnect.backend.repository;

import com.devconnect.backend.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    // Check if already following
    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // Get all follower IDs for a user (needed for fan-out)
    @Query("SELECT f.follower.id FROM Follow f WHERE f.following.id = :userId")
    List<Long> findFollowerIdsByFollowingId(Long userId);

    // Get all following IDs for a user (for profile page)
    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId")
    List<Long> findFollowingIdsByFollowerId(Long userId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowingId(Long userId);  // follower count
    long countByFollowerId(Long userId);   // following count

    void deleteByFollowerIdOrFollowingId(Long followerId, Long followingId);
}