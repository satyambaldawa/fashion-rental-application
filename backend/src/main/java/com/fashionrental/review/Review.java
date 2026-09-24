package com.fashionrental.review;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reviewer_name", nullable = false, length = 80)
    private String reviewerName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "item_description", nullable = false, length = 100)
    private String itemDescription;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "review_text", nullable = false, length = 256)
    private String reviewText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "submitter_ip", nullable = false, length = 45)
    private String submitterIp;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ReviewImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void addImage(ReviewImage image) {
        image.setReview(this);
        images.add(image);
    }

    public UUID getId() { return id; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getSubmitterIp() { return submitterIp; }
    public void setSubmitterIp(String submitterIp) { this.submitterIp = submitterIp; }
    public List<ReviewImage> getImages() { return images; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(OffsetDateTime moderatedAt) { this.moderatedAt = moderatedAt; }
}
