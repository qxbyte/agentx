package com.agentx.tools.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.tools.registry.ToolRegistry;
import com.agentx.tools.registry.ToolState;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ToolController {
    private final ToolRegistry toolRegistry;

    @GetMapping("/api/v1/tools")
    public ApiResponse<List<ToolView>> list() {
        return ApiResponse.ok(toolRegistry.catalog().stream().map(ToolView::of).toList());
    }

    @PatchMapping("/api/v1/admin/tools/{name}/enabled")
    public ApiResponse<ToolView> setEnabled(@PathVariable String name, @RequestParam boolean value) {
        return ApiResponse.ok(ToolView.of(toolRegistry.setEnabled(name, value)));
    }

    public record ToolView(String name, String source, String description,
                           String paramsSchema, boolean enabled) {
        static ToolView of(ToolState s) {
            return new ToolView(s.getName(), s.getSource(), s.getDescription(),
                    s.getParamsSchema(), s.isEnabled());
        }
    }
}
