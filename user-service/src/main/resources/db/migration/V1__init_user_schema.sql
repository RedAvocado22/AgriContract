CREATE TABLE user_profiles
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             VARCHAR(255) NOT NULL,
    organization_name   VARCHAR(255) NOT NULL,
    role                ENUM('SELLER', 'BUYER', 'ADMIN') NOT NULL,
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(50),
    address             TEXT,
    verification_status ENUM('PENDING', 'VERIFIED', 'REJECTED') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,


    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profiles_user_id (user_id)
);
