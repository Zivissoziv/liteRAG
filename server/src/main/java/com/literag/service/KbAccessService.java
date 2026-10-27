package com.literag.service;

import com.literag.common.BizException;
import com.literag.dto.Requests;
import com.literag.entity.KbAccess;
import com.literag.entity.KnowledgeBase;
import com.literag.entity.SysUser;
import com.literag.repository.KbAccessRepository;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.repository.SysUserRepository;
import com.literag.security.AuthContext;
import com.literag.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库的数据级可见性。
 *
 * <p>可见性只有两档：PUBLIC（所有登录用户）与 RESTRICTED（仅授权主体）。
 * 文档、目录、分块、检索结果都继承所属知识库的可见性，避免出现「库不可见但文档能预览」的漏洞。
 *
 * <p>管理员与无用户上下文的调用（MCP、入库线程）按全量处理：
 * 前者是超管语义，后者没有可判定的主体，若按受限处理会让后台入库失败。
 */
@Service
@RequiredArgsConstructor
public class KbAccessService {

    public static final String VIS_PUBLIC = "PUBLIC";
    public static final String VIS_RESTRICTED = "RESTRICTED";

    private final KbAccessRepository accessRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final SysUserRepository userRepository;

    /** 当前请求是否跳过可见性判断（管理员或后台线程） */
    public boolean unrestricted() {
        AuthContext.CurrentUser user = AuthContext.get();
        return user == null || SysUser.ROLE_ADMIN.equals(user.role());
    }

    /** 返回当前用户可见的知识库 id；返回 null 表示不受限 */
    public Set<Long> visibleKbIds() {
        if (unrestricted()) {
            return null;
        }
        AuthContext.CurrentUser user = AuthContext.get();
        List<KnowledgeBase> all = kbRepository.findAll();
        Map<Long, Set<String>> grants = new LinkedHashMap<>();
        for (KbAccess access : accessRepository.findAll()) {
            grants.computeIfAbsent(access.getKbId(), k -> new HashSet<>())
                    .add(access.getSubjectType() + ":" + access.getSubjectValue());
        }
        Set<Long> visible = new HashSet<>();
        for (KnowledgeBase kb : all) {
            if (isPublic(kb)) {
                visible.add(kb.getId());
                continue;
            }
            Set<String> subjects = grants.getOrDefault(kb.getId(), Set.of());
            if (subjects.contains(KbAccess.SUBJECT_ROLE + ":" + user.role())
                    || subjects.contains(KbAccess.SUBJECT_USER + ":" + user.id())) {
                visible.add(kb.getId());
            }
        }
        return visible;
    }

    public boolean canView(KnowledgeBase kb) {
        if (kb == null) {
            return false;
        }
        if (unrestricted() || isPublic(kb)) {
            return true;
        }
        AuthContext.CurrentUser user = AuthContext.get();
        for (KbAccess access : accessRepository.findByKbId(kb.getId())) {
            if (KbAccess.SUBJECT_ROLE.equals(access.getSubjectType())
                    && user.role().equals(access.getSubjectValue())) {
                return true;
            }
            if (KbAccess.SUBJECT_USER.equals(access.getSubjectType())
                    && String.valueOf(user.id()).equals(access.getSubjectValue())) {
                return true;
            }
        }
        return false;
    }

    public boolean canView(Long kbId) {
        if (kbId == null) {
            return false;
        }
        return canView(kbRepository.findById(kbId).orElse(null));
    }

    /** 不可见与不存在返回同样的 403，避免用状态码探测知识库是否存在 */
    public void assertCanView(Long kbId) {
        if (!canView(kbId)) {
            throw new BizException(403, "没有访问该知识库的权限");
        }
    }

    public List<KnowledgeBase> filter(List<KnowledgeBase> kbs) {
        Set<Long> visible = visibleKbIds();
        if (visible == null) {
            return kbs;
        }
        return kbs.stream().filter(kb -> visible.contains(kb.getId())).toList();
    }

    // ------------------------------------------------------------- 策略维护

    /** 按请求更新可见性；仅当请求显式带上 visibility 时才处理授权明细 */
    @Transactional
    public void apply(KnowledgeBase kb, Requests.KbReq req) {
        String visibility = normalize(req.getVisibility(), kb.getVisibility());
        kb.setVisibility(visibility);
        if (VIS_PUBLIC.equals(visibility)) {
            accessRepository.deleteByKbId(kb.getId());
            return;
        }
        if (req.getAccessRoles() == null && req.getAccessUserIds() == null) {
            return; // 只改别的字段，保留原授权
        }
        accessRepository.deleteByKbId(kb.getId());
        List<KbAccess> records = new ArrayList<>();
        if (req.getAccessRoles() != null) {
            req.getAccessRoles().stream()
                    .filter(Roles::isRole)
                    .distinct()
                    .forEach(role -> records.add(record(kb.getId(), KbAccess.SUBJECT_ROLE, role)));
        }
        if (req.getAccessUserIds() != null) {
            req.getAccessUserIds().stream()
                    .filter(userRepository::existsById)
                    .distinct()
                    .forEach(id -> records.add(record(kb.getId(), KbAccess.SUBJECT_USER, String.valueOf(id))));
        }
        if (records.isEmpty() && !unrestricted()) {
            throw new BizException(400, "受限知识库至少需要指定一个角色或用户");
        }
        accessRepository.saveAll(records);
    }

    /** 供编辑弹窗回填：角色列表与用户 id 列表 */
    public Map<String, Object> describe(Long kbId) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> roles = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        for (KbAccess access : accessRepository.findByKbId(kbId)) {
            if (KbAccess.SUBJECT_ROLE.equals(access.getSubjectType())) {
                roles.add(access.getSubjectValue());
            } else {
                try {
                    userIds.add(Long.parseLong(access.getSubjectValue()));
                } catch (NumberFormatException ignored) {
                    // 脏数据忽略
                }
            }
        }
        map.put("roles", roles);
        map.put("userIds", userIds);
        return map;
    }

    /** 授权可选范围：角色清单 + 启用中的用户（不含管理员，管理员本就全可见） */
    public Map<String, Object> options() {
        List<Map<String, Object>> users = userRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .filter(u -> !SysUser.ROLE_ADMIN.equals(u.getRole()))
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "displayName", u.getDisplayName() == null ? u.getUsername() : u.getDisplayName(),
                        "role", u.getRole(),
                        "roleLabel", Roles.label(u.getRole())))
                .toList();
        return Map.of("roles", Roles.describe().stream()
                .filter(r -> !SysUser.ROLE_ADMIN.equals(r.get("role")))
                .toList(), "users", users);
    }

    @Transactional
    public void deleteByKb(Long kbId) {
        accessRepository.deleteByKbId(kbId);
    }

    public static boolean isPublic(KnowledgeBase kb) {
        return kb.getVisibility() == null || VIS_PUBLIC.equals(kb.getVisibility());
    }

    private KbAccess record(Long kbId, String type, String value) {
        KbAccess access = new KbAccess();
        access.setKbId(kbId);
        access.setSubjectType(type);
        access.setSubjectValue(value);
        return access;
    }

    private String normalize(String requested, String current) {
        if (requested == null || requested.isBlank()) {
            return current == null ? VIS_PUBLIC : current;
        }
        String value = requested.trim().toUpperCase();
        if (!VIS_PUBLIC.equals(value) && !VIS_RESTRICTED.equals(value)) {
            throw new BizException(400, "可见性只能是 PUBLIC 或 RESTRICTED");
        }
        return value;
    }
}
