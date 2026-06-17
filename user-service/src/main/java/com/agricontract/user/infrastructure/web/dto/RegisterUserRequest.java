package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.domain.model.vo.Role;


public record RegisterUserRequest(String organizationName,
                                  Role role,
                                  String phone,
                                  String address
) {


}
