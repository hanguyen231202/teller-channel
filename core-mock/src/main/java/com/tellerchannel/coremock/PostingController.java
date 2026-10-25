package com.tellerchannel.coremock;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/core/postings")
public class PostingController {

    private final PostingService postingService;
    private final ChaosService chaosService;

    public PostingController(PostingService postingService, ChaosService chaosService) {
        this.postingService = postingService;
        this.chaosService = chaosService;
    }

    /** 201 khi vừa hạch toán, 200 khi là lệnh trùng (trả lại bút toán cũ). */
    @PostMapping
    public ResponseEntity<Posting> post(@Valid @RequestBody PostingRequest request) {
        chaosService.beforePosting();
        PostingService.PostingResult result = postingService.post(request);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.posting());
    }

    @GetMapping("/{referenceNo}")
    public Posting get(@PathVariable String referenceNo) {
        chaosService.beforeInquiry();
        return postingService.findByReference(referenceNo)
                .orElseThrow(() -> new CoreBusinessException(HttpStatus.NOT_FOUND, "POSTING_NOT_FOUND",
                        "No posting with reference " + referenceNo));
    }
}
