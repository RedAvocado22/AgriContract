package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserRequest {
    private String organizationName;
    private Role role;
    private String phone;
    private String address;
}
