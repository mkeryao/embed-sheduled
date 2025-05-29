package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskUser;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for {@link TaskUser} entities.
 * Defines methods for CRUD operations and specific queries related to users.
 */
public interface TaskUserDao {
    /**
     * Saves a new user or updates an existing one.
     * @param user The user entity to save.
     * @return The saved user entity, potentially with a generated ID if new.
     */
    TaskUser save(TaskUser user);

    /**
     * Finds a user by their ID.
     * @param userId The ID of the user.
     * @return An {@link Optional} containing the user if found, or empty otherwise.
     */
    Optional<TaskUser> findById(Integer userId);

    /**
     * Finds a user by their username.
     * @param username The username to search for.
     * @return An {@link Optional} containing the user if found, or empty otherwise.
     */
    Optional<TaskUser> findByUsername(String username);

    /**
     * Retrieves all users.
     * @return A list of all users.
     */
    List<TaskUser> findAll();

    /**
     * Updates an existing user.
     * @param user The user entity with updated values.
     * @return The number of rows affected.
     */
    int update(TaskUser user);

    /**
     * Deletes a user by their ID.
     * @param userId The ID of the user to delete.
     * @return The number of rows affected.
     */
    int deleteById(Integer userId);
}
