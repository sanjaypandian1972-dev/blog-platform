package com.example.blogplatform.controller;

import com.example.blogplatform.model.Blog;
import com.example.blogplatform.repository.BlogRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BlogController {

    private final BlogRepository repository;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();
    public BlogController(BlogRepository repository) {
        this.repository = repository;
    }

    // REGISTER
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Blog blog) {
        if (blog.getUsername() == null ||
            blog.getPassword() == null ||
            blog.getUsername().isBlank() ||
            blog.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username and password required"));
        }

        List<Blog> users =
                repository.findByTypeAndUsername("USER", blog.getUsername());

        if (!users.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username already exists"));
        }


        Blog user = new Blog();
        user.setType("USER");
        user.setUsername(blog.getUsername());
        user.setPassword(passwordEncoder.encode(blog.getPassword()));

       repository.save(user);


        return ResponseEntity.ok(
                Map.of("message", "Registration successful"));
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Blog blog, HttpSession session) {

        List<Blog> users =
                repository.findByTypeAndUsername("USER", blog.getUsername());

        if (users.isEmpty() ||
        	     !passwordEncoder.matches(
        	        blog.getPassword(), users.get(0).getPassword())) {

        	    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        	            .body(Map.of("message", "Invalid username or password"));
        	}

        Blog user = users.get(0);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "username", user.getUsername(),
                "userId", user.getId()
        ));
    }

    // LOGOUT
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // GET ALL POSTS
    @GetMapping("/posts")
    public List<Blog> getPosts() {
        return repository.findByTypeOrderByCreatedAtDesc("POST");
    }

    // CREATE POST
    @PostMapping("/posts")
    public ResponseEntity<?> createPost(
            @RequestBody Blog blog, HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in first"));
        }

        if (blog.getTitle() == null || blog.getTitle().isBlank()
                || blog.getContent() == null
                || blog.getContent().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Title and content required"));
        }

        Blog post = new Blog();
        post.setType("POST");
        post.setTitle(blog.getTitle());
        post.setContent(blog.getContent());
        post.setUserId(userId);
        post.setUsername(username);

        return ResponseEntity.ok(repository.save(post));
    }

    // EDIT POST
    @PutMapping("/posts/{id}")
    public ResponseEntity<?> editPost(
            @PathVariable Long id,
            @RequestBody Blog updated,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in first"));
        }

        Blog post = repository.findById(id).orElse(null);

        if (post == null || !"POST".equals(post.getType())) {
            return ResponseEntity.notFound().build();
        }

        if (!userId.equals(post.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot edit this post"));
        }

        post.setTitle(updated.getTitle());
        post.setContent(updated.getContent());

        return ResponseEntity.ok(repository.save(post));
    }

    // DELETE POST
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long id, HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in first"));
        }

        Blog post = repository.findById(id).orElse(null);

        if (post == null || !"POST".equals(post.getType())) {
            return ResponseEntity.notFound().build();
        }

        if (!userId.equals(post.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot delete this post"));
        }

        // Delete comments belonging to this post
        List<Blog> comments =
                repository.findByTypeAndPostIdOrderByCreatedAtAsc(
                        "COMMENT", id);
        repository.deleteAll(comments);
        repository.delete(post);

        return ResponseEntity.ok(Map.of("message", "Post deleted"));
    }

    // GET COMMENTS
    @GetMapping("/posts/{postId}/comments")
    public List<Blog> getComments(@PathVariable Long postId) {
        return repository.findByTypeAndPostIdOrderByCreatedAtAsc(
                "COMMENT", postId);
    }

    // ADD COMMENT
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<?> addComment(
            @PathVariable Long postId,
            @RequestBody Blog input,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in first"));
        }

        Blog post = repository.findById(postId).orElse(null);

        if (post == null || !"POST".equals(post.getType())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Post not found"));
        }

        if (input.getContent() == null || input.getContent().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Comment cannot be empty"));
        }

        Blog comment = new Blog();
        comment.setType("COMMENT");
        comment.setContent(input.getContent());
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setUsername(username);

        return ResponseEntity.ok(repository.save(comment));
    }

    // DELETE COMMENT
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long id, HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in first"));
        }

        Blog comment = repository.findById(id).orElse(null);

        if (comment == null || !"COMMENT".equals(comment.getType())) {
            return ResponseEntity.notFound().build();
        }

        if (!userId.equals(comment.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot delete this comment"));
        }

        repository.delete(comment);
        return ResponseEntity.ok(Map.of("message", "Comment deleted"));
    }
}