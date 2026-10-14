package com.example.blogplatform.repository;

import com.example.blogplatform.model.Blog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    List<Blog> findByTypeOrderByCreatedAtDesc(String type);

    List<Blog> findByTypeAndPostIdOrderByCreatedAtAsc(
            String type, Long postId);

    List<Blog> findByTypeAndUsername(String type, String username);
}
