package com.bss.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("Should create user with correct initial state")
    void shouldCreateUserWithCorrectInitialState() {
        User user = new User("John Doe", "12345678900", "john@test.com", Role.ROLE_EMPLOYEE);

        assertEquals("John Doe", user.getName());
        assertEquals("12345678900", user.getDocument());
        assertEquals("john@test.com", user.getEmail());
        assertEquals(Role.ROLE_EMPLOYEE, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when creating user with invalid data")
    void shouldThrowExceptionWhenCreatingUserWithInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new User(null, "123", "email", Role.ROLE_EMPLOYEE));
        assertThrows(IllegalArgumentException.class, () -> new User("", "123", "email", Role.ROLE_EMPLOYEE));
        assertThrows(IllegalArgumentException.class, () -> new User("Name", null, "email", Role.ROLE_EMPLOYEE));
        assertThrows(IllegalArgumentException.class, () -> new User("Name", "123", null, Role.ROLE_EMPLOYEE));
    }

    @Test
    @DisplayName("Should block and activate user")
    void shouldBlockAndActivateUser() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);

        user.block();
        assertEquals(UserStatus.BLOCKED, user.getStatus());

        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("Should change name successfully")
    void shouldChangeNameSuccessfully() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);
        user.changeName("Jane Doe");
        assertEquals("Jane Doe", user.getName());
    }

    @Test
    @DisplayName("Should not change name if null or blank")
    void shouldNotChangeNameIfInvalid() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);
        
        user.changeName(null);
        assertEquals("John", user.getName());

        user.changeName("");
        assertEquals("John", user.getName());
        
        user.changeName("   ");
        assertEquals("John", user.getName());
    }

    @Test
    @DisplayName("Should change email successfully")
    void shouldChangeEmailSuccessfully() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);
        user.changeEmail("new@test.com");
        assertEquals("new@test.com", user.getEmail());
    }

    @Test
    @DisplayName("Should not change email if null or blank")
    void shouldNotChangeEmailIfInvalid() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);
        
        user.changeEmail(null);
        assertEquals("email", user.getEmail());

        user.changeEmail("");
        assertEquals("email", user.getEmail());
    }

    @Test
    @DisplayName("Should change role successfully")
    void shouldChangeRoleSuccessfully() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);
        user.changeRole(Role.ROLE_ADMIN);
        assertEquals(Role.ROLE_ADMIN, user.getRole());
    }

    @Test
    @DisplayName("Should throw exception when changing role to null")
    void shouldThrowExceptionWhenChangingRoleToNull() {
        User user = new User("John", "123", "email", Role.ROLE_EMPLOYEE);
        assertThrows(IllegalArgumentException.class, () -> user.changeRole(null));
    }

    @Test
    @DisplayName("Should verify equality based on ID")
    void shouldVerifyEqualityBasedOnId() {
        User u1 = new User("John", "111", "e1", Role.ROLE_EMPLOYEE);
        User u2 = new User("John", "111", "e1", Role.ROLE_EMPLOYEE);
        User u3 = new User("Jane", "222", "e2", Role.ROLE_EMPLOYEE);

        ReflectionTestUtils.setField(u1, "id", 1L);
        ReflectionTestUtils.setField(u2, "id", 1L);
        ReflectionTestUtils.setField(u3, "id", 2L);

        // Same ID -> Equal
        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());

        // Different ID -> Not Equal
        assertNotEquals(u1, u3);
        assertNotEquals(u1.hashCode(), u3.hashCode());
    }

    @Test
    @DisplayName("Should not be equal to null or different class")
    void shouldNotBeEqualToNullOrDifferentClass() {
        User u = new User("John", "111", "e1", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(u, "id", 1L);

        assertNotEquals(null, u);
        assertNotEquals(u, new Object());
    }

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
        User u = new User("John", "111", "e1", Role.ROLE_EMPLOYEE);
        assertEquals(u, u);
    }
}
