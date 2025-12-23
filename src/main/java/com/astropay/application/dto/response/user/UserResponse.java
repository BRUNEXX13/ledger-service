package com.astropay.application.dto.response.user;

import com.astropay.application.util.AppConstants;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.UserStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.Objects;

@JsonRootName(value = "user")
@Relation(collectionRelation = "users")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse extends RepresentationModel<UserResponse> {

    private Long id;
    private String name;
    private String document;
    private String email;
    private UserStatus status;
    private Role role;

    @JsonFormat(pattern = AppConstants.DATE_TIME_FORMAT, timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(pattern = AppConstants.DATE_TIME_FORMAT, timezone = "UTC")
    private Instant updatedAt;

    public UserResponse() {
    }

    // Construtor, Getters e Setters
    public UserResponse(Long id, String name, String document, String email, UserStatus status, Role role, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.email = email;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDocument() { return document; }
    public void setDocument(String document) { this.document = document; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UserResponse that = (UserResponse) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(document, that.document) &&
                Objects.equals(email, that.email) &&
                status == that.status &&
                role == that.role &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, name, document, email, status, role, createdAt, updatedAt);
    }

    public static class Builder {
        private Long id;
        private String name;
        private String document;
        private String email;
        private UserStatus status;
        private Role role;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder document(String document) { this.document = document; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder status(UserStatus status) { this.status = status; return this; }
        public Builder role(Role role) { this.role = role; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public UserResponse build() {
            return new UserResponse(id, name, document, email, status, role, createdAt, updatedAt);
        }
    }
}
