package com.literag.dto;

import com.literag.entity.SysUser;
import com.literag.security.Roles;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDto {

    private Long id;
    private String username;
    private String displayName;
    private String role;
    private String roleLabel;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private List<String> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public static UserDto from(SysUser user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName());
        dto.setRole(user.getRole());
        dto.setRoleLabel(Roles.label(user.getRole()));
        dto.setEnabled(user.getEnabled());
        dto.setMustChangePassword(user.getMustChangePassword());
        dto.setPermissions(Roles.ordered(user.getRole()));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        return dto;
    }
}
