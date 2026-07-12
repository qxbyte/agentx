package com.agentx.rag.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.common.api.ApiResponse;
import com.agentx.rag.service.DocumentService;
import com.agentx.rag.service.HitTestService;
import com.agentx.rag.service.KnowledgeBaseService;
import com.agentx.rag.web.dto.RagDtos.DocView;
import com.agentx.rag.web.dto.RagDtos.HitTestRequest;
import com.agentx.rag.web.dto.RagDtos.HitView;
import com.agentx.rag.web.dto.RagDtos.KbUpsertRequest;
import com.agentx.rag.web.dto.RagDtos.KbView;
import com.agentx.rag.web.dto.RagDtos.SegmentUpdateRequest;
import com.agentx.rag.web.dto.RagDtos.SegmentView;
import com.agentx.rag.web.dto.RagDtos.TaskView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class RagController {

    private final KnowledgeBaseService kbService;
    private final DocumentService documentService;
    private final HitTestService hitTestService;

    // ---- 知识库 ----

    @GetMapping
    public ApiResponse<List<KbView>> list(@CurrentUser AuthPrincipal user) {
        return ApiResponse.ok(kbService.list(user.id()).stream().map(KbView::of).toList());
    }

    @PostMapping
    public ApiResponse<KbView> create(@CurrentUser AuthPrincipal user,
                                      @Valid @RequestBody KbUpsertRequest req) {
        return ApiResponse.ok(KbView.of(kbService.create(user.id(), req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<KbView> update(@CurrentUser AuthPrincipal user, @PathVariable UUID id,
                                      @Valid @RequestBody KbUpsertRequest req) {
        return ApiResponse.ok(KbView.of(kbService.update(id, user.id(), req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUser AuthPrincipal user, @PathVariable UUID id) {
        kbService.delete(id, user.id());
        return ApiResponse.ok();
    }

    // ---- 文档 ----

    @PostMapping("/{kbId}/documents")
    public ApiResponse<DocView> upload(@CurrentUser AuthPrincipal user, @PathVariable UUID kbId,
                                       @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(DocView.of(documentService.upload(kbId, user.id(), file)));
    }

    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<DocView>> documents(@CurrentUser AuthPrincipal user,
                                                @PathVariable UUID kbId) {
        return ApiResponse.ok(documentService.list(kbId, user.id()).stream()
                .map(DocView::of).toList());
    }

    @DeleteMapping("/documents/{docId}")
    public ApiResponse<Void> deleteDocument(@CurrentUser AuthPrincipal user,
                                            @PathVariable UUID docId) {
        documentService.delete(docId, user.id());
        return ApiResponse.ok();
    }

    @PostMapping("/documents/{docId}/reingest")
    public ApiResponse<TaskView> reingest(@CurrentUser AuthPrincipal user,
                                          @PathVariable UUID docId) {
        return ApiResponse.ok(TaskView.of(documentService.reingest(docId, user.id())));
    }

    @GetMapping("/documents/{docId}/task")
    public ApiResponse<TaskView> task(@CurrentUser AuthPrincipal user, @PathVariable UUID docId) {
        return ApiResponse.ok(TaskView.of(documentService.latestTask(docId, user.id())));
    }

    // ---- 分段 ----

    @GetMapping("/documents/{docId}/segments")
    public ApiResponse<List<SegmentView>> segments(@CurrentUser AuthPrincipal user,
                                                   @PathVariable UUID docId) {
        return ApiResponse.ok(documentService.segments(docId, user.id()).stream()
                .map(SegmentView::of).toList());
    }

    @PutMapping("/segments/{segmentId}")
    public ApiResponse<SegmentView> updateSegment(@CurrentUser AuthPrincipal user,
                                                  @PathVariable UUID segmentId,
                                                  @Valid @RequestBody SegmentUpdateRequest req) {
        return ApiResponse.ok(SegmentView.of(
                documentService.updateSegment(segmentId, user.id(), req.content())));
    }

    @PatchMapping("/segments/{segmentId}/enabled")
    public ApiResponse<SegmentView> toggleSegment(@CurrentUser AuthPrincipal user,
                                                  @PathVariable UUID segmentId,
                                                  @RequestParam boolean value) {
        return ApiResponse.ok(SegmentView.of(
                documentService.toggleSegment(segmentId, user.id(), value)));
    }

    // ---- 命中测试 ----

    @PostMapping("/{kbId}/hit-test")
    public ApiResponse<List<HitView>> hitTest(@CurrentUser AuthPrincipal user,
                                              @PathVariable UUID kbId,
                                              @Valid @RequestBody HitTestRequest req) {
        return ApiResponse.ok(hitTestService.hitTest(kbId, user.id(), req));
    }
}
