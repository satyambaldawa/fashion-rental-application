package com.fashionrental.review;

final class ReviewTestFixtures {

    private ReviewTestFixtures() {
    }

    static Review approvedReview() {
        Review review = new Review();
        review.setReviewerName("Priya S");
        review.setPhone("9876543210");
        review.setItemDescription("Red bridal lehenga");
        review.setRating(5);
        review.setReviewText("Beautiful outfit, fit perfectly.");
        review.setStatus(Review.Status.APPROVED);
        review.setSubmitterIp("203.0.113.7");

        ReviewImage image = new ReviewImage();
        image.setUrl("full-1");
        image.setThumbnailUrl("thumb-1");
        image.setSortOrder(0);
        review.addImage(image);

        return review;
    }
}
