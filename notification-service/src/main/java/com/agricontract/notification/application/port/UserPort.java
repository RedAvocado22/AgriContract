package com.agricontract.notification.application.port;


import com.agricontract.notification.application.port.dto.UserInfo;

public interface UserPort {
    UserInfo getUser(String userId);
}
