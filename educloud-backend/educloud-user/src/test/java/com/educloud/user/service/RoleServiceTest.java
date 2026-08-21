package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.RoleCreateRequest;
import com.educloud.user.dto.request.RoleUpdateRequest;
import com.educloud.user.dto.response.RoleResponse;
import com.educloud.user.entity.SysRoleEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.support.AuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 角色维护单元测试（code 唯一、内置角色保护）。 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private AuditWriter auditWriter;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(sysRoleMapper, auditWriter);
    }

    @Test
    void createMapsDuplicateCodeToConflict() {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_role_code"))
                .when(sysRoleMapper).insert(any(SysRoleEntity.class));

        assertThatThrownBy(() -> service.create(
                new RoleCreateRequest("TEACHER", "教师", null), "1", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    void builtInRoleCannotBeModified() {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(1L);
        role.setCode("STUDENT");
        role.setBuiltIn(true);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);

        assertThatThrownBy(() -> service.update(
                1L, new RoleUpdateRequest("改名", null), "1", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class);
        verify(sysRoleMapper, never()).updateById(any(SysRoleEntity.class));
    }

    @Test
    void updatesCustomRole() {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(9L);
        role.setCode("TUTOR");
        role.setBuiltIn(false);
        when(sysRoleMapper.selectById(9L)).thenReturn(role);

        RoleResponse response = service.update(
                9L, new RoleUpdateRequest("助教", "辅助教学"), "1", "ip", "ua", "req-1");

        assertThat(response.name()).isEqualTo("助教");
        verify(sysRoleMapper).updateById(role);
    }
}
