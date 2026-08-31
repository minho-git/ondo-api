package com.ondo.wholesale.security.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트 검증 전용 컨트롤러(테스트 소스). 실제 /me 등은 MUL-70/71이 채운다.
 * 임의 보호 경로 + 게이트 예외 경로(/api/wholesale/me, /me/reapply)를 임시로 핸들한다.
 */
@RestController
public class ProtectedTestController {

    public record Message(String value) {}

    @GetMapping("/test/protected")
    public Message protectedResource() {
        return new Message("protected");
    }

    @GetMapping("/api/wholesale/me")
    public Message me() {
        return new Message("me");
    }

    @PostMapping("/api/wholesale/me/reapply")
    public Message reapply() {
        return new Message("reapplied");
    }
}
