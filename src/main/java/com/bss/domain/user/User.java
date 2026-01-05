package com.bss.domain.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_user", indexes = {
    @Index(name = "idx_user_document", columnList = "document", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String document; // Brazilian individual taxpayer registry ID

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private Role role;

    @CreatedDate
    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Deprecated
    protected User() {}

    // Constructor adjusted for use by the mapper
    public User(String name, String document, String email, Role role) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("User name cannot be blank.");
        if (document == null || document.isBlank()) throw new IllegalArgumentException("User document cannot be blank.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("User email cannot be blank.");

        this.name = name;
        this.document = document;
        this.email = email;
        this.role = role; // Can be null initially, will be set by the service
        this.status = UserStatus.ACTIVE;
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDocument() { return document; }
    public String getEmail() { return email; }
    public UserStatus getStatus() { return status; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Business methods
    public void block() { this.status = UserStatus.BLOCKED; }
    public void activate() { this.status = UserStatus.ACTIVE; }
    
    public void changeName(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
    }

    public void changeEmail(String newEmail) {
        if (newEmail != null && !newEmail.isBlank()) {
            this.email = newEmail;
        }
    }
    
    public void changeRole(Role newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("Role cannot be null.");
        }
        this.role = newRole;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
