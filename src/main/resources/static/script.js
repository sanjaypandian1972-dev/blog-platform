const API = "/api";

let currentUser = JSON.parse(sessionStorage.getItem("blogUser") || "null");
let editingPostId = null;

const $ = id => document.getElementById(id);

async function request(url, options = {}) {
    const response = await fetch(API + url, {
        ...options,
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(data.message || "Something went wrong");
    }

    return data;
}

function showMessage(message) {
    $("message").textContent = message;
}

function updateAccount() {
    $("authPanel").classList.toggle("hidden", !!currentUser);
    $("editorPanel").classList.toggle("hidden", !currentUser);

    $("account").replaceChildren();

    if (currentUser) {
        const label = document.createElement("span");
        label.textContent = "Logged in as " + currentUser.username + " ";

        const logout = document.createElement("button");
        logout.textContent = "Log out";
        logout.addEventListener("click", logoutUser);

        $("account").append(label, logout);
    }
}

async function register(event) {
    event.preventDefault();

    try {
        const result = await request("/register", {
            method: "POST",
            body: JSON.stringify({
                username: $("regUsername").value.trim(),
                password: $("regPassword").value
            })
        });

        showMessage(result.message + ". You can now log in.");
        $("registerForm").reset();
    } catch (error) {
        showMessage(error.message);
    }
}

async function login(event) {
    event.preventDefault();

    try {
        const result = await request("/login", {
            method: "POST",
            body: JSON.stringify({
                username: $("loginUsername").value.trim(),
                password: $("loginPassword").value
            })
        });

        currentUser = {
            id: result.userId,
            username: result.username
        };

        sessionStorage.setItem("blogUser", JSON.stringify(currentUser));
        $("loginForm").reset();
        updateAccount();
        await loadPosts();
        showMessage("Login successful!");
    } catch (error) {
        showMessage(error.message);
    }
}

async function logoutUser() {
    try {
        await request("/logout", { method: "POST" });
    } catch (error) {
        showMessage(error.message);
        return;
    }

    currentUser = null;
    sessionStorage.removeItem("blogUser");
    resetEditor();
    updateAccount();
    await loadPosts();
    showMessage("You have logged out.");
}

function resetEditor() {
    editingPostId = null;
    $("postForm").reset();
    $("editorTitle").textContent = "Write a blog post";
    $("savePost").textContent = "Publish Post";
    $("cancelEdit").classList.add("hidden");
}

async function savePost(event) {
    event.preventDefault();

    if (!currentUser) {
        showMessage("Please log in first.");
        return;
    }

    const post = {
        title: $("postTitle").value.trim(),
        content: $("postContent").value.trim()
    };

    try {
        await request(
            editingPostId ? `/posts/${editingPostId}` : "/posts",
            {
                method: editingPostId ? "PUT" : "POST",
                body: JSON.stringify(post)
            }
        );

        resetEditor();
        await loadPosts();
        showMessage("Post saved successfully!");
    } catch (error) {
        showMessage(error.message);
    }
}

function editPost(post) {
    editingPostId = post.id;
    $("postTitle").value = post.title || "";
    $("postContent").value = post.content || "";
    $("editorTitle").textContent = "Edit blog post";
    $("savePost").textContent = "Save Changes";
    $("cancelEdit").classList.remove("hidden");
    $("editorPanel").scrollIntoView({ behavior: "smooth" });
}

async function deletePost(id) {
    if (!confirm("Delete this post and its comments?")) return;

    try {
        await request(`/posts/${id}`, { method: "DELETE" });
        await loadPosts();
        showMessage("Post deleted.");
    } catch (error) {
        showMessage(error.message);
    }
}

async function addComment(postId, input) {
    if (!currentUser) {
        showMessage("Please log in to comment.");
        return;
    }

    const content = input.value.trim();
    if (!content) return;

    try {
        await request(`/posts/${postId}/comments`, {
            method: "POST",
            body: JSON.stringify({ content })
        });

        await loadPosts();
        showMessage("Comment added.");
    } catch (error) {
        showMessage(error.message);
    }
}

async function deleteComment(id) {
    try {
        await request(`/comments/${id}`, { method: "DELETE" });
        await loadPosts();
        showMessage("Comment deleted.");
    } catch (error) {
        showMessage(error.message);
    }
}

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function addButton(parent, text, action, className = "") {
    const button = element("button", className, text);
    button.addEventListener("click", action);
    parent.appendChild(button);
    return button;
}

async function loadPosts() {
    const container = $("posts");
    container.replaceChildren();

    try {
        const posts = await request("/posts");

        if (!posts.length) {
            container.appendChild(element(
                "p", "", "No blog posts yet. Be the first to write one!"
            ));
            return;
        }

        for (const post of posts) {
            const card = element("article", "post-card");
            card.appendChild(element("h3", "", post.title));
            card.appendChild(element(
                "p", "post-meta",
                `By ${post.username || "User"} · ${
                    post.createdAt
                        ? new Date(post.createdAt).toLocaleString()
                        : ""
                }`
            ));
            card.appendChild(element("p", "post-content", post.content));

            if (currentUser && currentUser.id === post.userId) {
                addButton(card, "Edit", () => editPost(post));
                addButton(
                    card, "Delete", () => deletePost(post.id), "danger"
                );
            }

            const section = element("div", "comments");
            section.appendChild(element("h4", "", "Comments"));

            try {
                const comments = await request(
                    `/posts/${post.id}/comments`
                );

                for (const comment of comments) {
                    const item = element("div", "comment");
                    item.appendChild(element(
                        "p", "", comment.content
                    ));
                    item.appendChild(element(
                        "small", "post-meta",
                        "By " + (comment.username || "User")
                    ));

                    if (currentUser && currentUser.id === comment.userId) {
                        addButton(
                            item, "Delete comment",
                            () => deleteComment(comment.id), "danger"
                        );
                    }

                    section.appendChild(item);
                }
            } catch (error) {
                section.appendChild(element(
                    "p", "", "Unable to load comments."
                ));
            }

            if (currentUser) {
                const form = element("form", "comment-form");
                const input = element("input");
                input.placeholder = "Write a comment...";
                input.required = true;
                input.maxLength = 2000;

                const submit = element("button", "", "Comment");
                submit.type = "submit";

                form.append(input, submit);
                form.addEventListener("submit", event => {
                    event.preventDefault();
                    addComment(post.id, input);
                });

                section.appendChild(form);
            }

            card.appendChild(section);
            container.appendChild(card);
        }
    } catch (error) {
        showMessage("Unable to load posts: " + error.message);
    }
}

$("registerForm").addEventListener("submit", register);
$("loginForm").addEventListener("submit", login);
$("postForm").addEventListener("submit", savePost);

$("cancelEdit").addEventListener("click", resetEditor);

updateAccount();
loadPosts();