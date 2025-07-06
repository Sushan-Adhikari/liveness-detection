package com.example.pensionerverification.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "users") // Use "users" as "user" is a reserved keyword in PostgreSQL
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // Path to a stored reference profile picture (optional for this flow, but good
    // to have)
    private String profilePicturePath;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastVerificationDate;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfilePicturePath() {
        return profilePicturePath;
    }

    public void setProfilePicturePath(String profilePicturePath) {
        this.profilePicturePath = profilePicturePath;
    }

    public Date getLastVerificationDate() {
        return lastVerificationDate;
    }

    public void setLastVerificationDate(Date lastVerificationDate) {
        this.lastVerificationDate = lastVerificationDate;
    }
}