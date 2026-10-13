package com.agricontract.product.application.port;

public interface UserContactPort {

    /**
     * Returns the contact email user-service holds for the given user.
     *
     * @throws com.agricontract.product.common.exception.UserProfileNotFoundException if the user has no profile
     * @throws com.agricontract.product.common.exception.UserServiceUnavailableException if the email cannot be resolved
     */
    String getEmail(String userId);
}
