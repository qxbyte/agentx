package com.agentx.skill;

import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.security.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillApiTest {

    @Autowired MockMvc mvc;
    @Autowired SysUserRepository userRepository;

    private Authentication asAdmin() {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(admin.getId(), admin.getUsername(), admin.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void crudFlowOnFileStore() throws Exception {
        String name = "t-" + Long.toHexString(System.nanoTime());
        String body = """
                {"name":"%s","description":"翻译为英文","argumentHint":"[文本]",
                 "content":"把以下内容翻译成英文：$ARGUMENTS"}""".formatted(name);

        // 创建 → 详情含 content，id 即 name
        mvc.perform(post("/api/v1/skills").with(authentication(asAdmin()))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(name))
                .andExpect(jsonPath("$.data.content").value("把以下内容翻译成英文：$ARGUMENTS"));

        // 补全菜单元数据：含该 skill 且不暴露 content
        mvc.perform(get("/api/v1/skills").with(authentication(asAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem(name)))
                .andExpect(jsonPath("$.data[?(@.name=='%s')].content".formatted(name)).isEmpty());

        // 重名冲突 409
        mvc.perform(post("/api/v1/skills").with(authentication(asAdmin()))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        // 非法 name 400
        mvc.perform(post("/api/v1/skills").with(authentication(asAdmin()))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Bad Name\",\"description\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isBadRequest());

        // 按 name 取详情
        mvc.perform(get("/api/v1/skills/" + name).with(authentication(asAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("翻译为英文"));

        // 停用后从菜单消失（frontmatter enabled: false 回写）
        mvc.perform(patch("/api/v1/skills/" + name + "/enabled").with(authentication(asAdmin()))
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/skills").with(authentication(asAdmin())))
                .andExpect(jsonPath("$.data[*].name", not(hasItem(name))));

        // 清理；再取 404
        mvc.perform(delete("/api/v1/skills/" + name).with(authentication(asAdmin())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/skills/" + name).with(authentication(asAdmin())))
                .andExpect(status().isNotFound());
    }
}
