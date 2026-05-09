package com.devconnect.backend.repository;

import com.devconnect.backend.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    // Get all posts by a user, newest first — for profile page
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    // Get all posts, newest first — for global feed
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    void deleteByAuthorId(Long authorId);
}