package com.impactiq.controller;

import com.impactiq.dto.DraftDtos.AzureDevOpsDraftRequest;
import com.impactiq.dto.DraftDtos.DraftRequest;
import com.impactiq.dto.DraftDtos.DraftResponse;
import com.impactiq.security.CurrentUserService;
import com.impactiq.service.DefectDraftService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Returns editable AI suggestions. Nothing is saved until the client submits the normal create request. */
@RestController
@RequestMapping("/api/defects/draft")
public class DefectDraftController {

    private final DefectDraftService draftService;
    private final CurrentUserService currentUser;

    public DefectDraftController(DefectDraftService draftService, CurrentUserService currentUser) {
        this.draftService = draftService;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public DraftResponse draft(@Valid @RequestBody DraftRequest request) {
        return draftService.draft(currentUser.require(), request);
    }

    @PostMapping(value = "/azure-devops", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DraftResponse draftFromAzureDevOps(@Valid @RequestBody AzureDevOpsDraftRequest request) {
        return draftService.draftFromAzureDevOps(currentUser.require(), request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftResponse draftFromUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long componentId
    ) {
        return draftService.draftFromUpload(currentUser.require(), file, note, projectId, componentId);
    }
}
