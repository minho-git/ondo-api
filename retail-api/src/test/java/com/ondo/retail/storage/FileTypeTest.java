package com.ondo.retail.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파일 앞머리로 진짜 형식을 알아내는지 (MUL-99).
 *
 * <p><b>절반이 "안 통과하는지" 를 보는 테스트다.</b> 전에는 {@code Content-Type} 만 봐서
 * 이름만 바꾼 실행 파일이 그대로 올라갔다.
 */
class FileTypeTest {

    /** JPEG 은 FF D8 FF 로 시작한다. */
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    /** PNG 는 89 "PNG" 뒤에 전송 깨짐을 잡는 네 바이트가 더 붙는다. */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /** PDF 는 아스키로 "%PDF". */
    private static final byte[] PDF_HEADER = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

    /** 리눅스 실행 파일. 7F 뒤에 "ELF". */
    private static final byte[] ELF_HEADER = {0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00};

    // ── 통과해야 하는 것 ────────────────────────────────────────

    @Test
    @DisplayName("jpg · png · pdf 를 알아본다")
    void 허용_형식을_알아본다() {
        assertThat(FileType.of(파일("a.jpg", "image/jpeg", JPEG_HEADER))).contains(FileType.JPEG);
        assertThat(FileType.of(파일("a.png", "image/png", PNG_HEADER))).contains(FileType.PNG);
        assertThat(FileType.of(파일("a.pdf", "application/pdf", PDF_HEADER))).contains(FileType.PDF);
    }

    @Test
    @DisplayName("이름과 Content-Type 이 없어도 앞머리만 맞으면 알아본다")
    void 이름이_없어도_된다() {
        // 판단 근거가 파일 내용뿐이라는 뜻이다
        assertThat(FileType.of(파일(null, null, PDF_HEADER))).contains(FileType.PDF);
    }

    // ── 막아야 하는 것 ──────────────────────────────────────────

    @Test
    @DisplayName("실행 파일에 jpg 이름과 image/jpeg 를 붙여도 막힌다")
    void 확장자만_바꾼_실행파일은_막힌다() {
        // 전에는 이게 그대로 통과했다. Content-Type 은 브라우저가 보내는 값이라 조작된다
        assertThat(FileType.of(파일("사업자등록증.jpg", "image/jpeg", ELF_HEADER))).isEmpty();
    }

    @Test
    @DisplayName("빈 파일은 막힌다")
    void 빈_파일은_막힌다() {
        assertThat(FileType.of(파일("a.jpg", "image/jpeg", new byte[0]))).isEmpty();
    }

    @Test
    @DisplayName("앞머리가 잘린 파일은 막힌다")
    void 잘린_파일은_막힌다() {
        // PNG 는 8바이트가 다 맞아야 한다. 앞 세 개만 맞는 건 PNG 가 아니다
        assertThat(FileType.of(파일("a.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E})))
                .isEmpty();
    }

    @Test
    @DisplayName("모르는 형식은 막힌다")
    void 모르는_형식은_막힌다() {
        assertThat(FileType.of(파일("a.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII))))
                .isEmpty();
        assertThat(FileType.of(파일("a.txt", "text/plain", "그냥 글자".getBytes(StandardCharsets.UTF_8))))
                .isEmpty();
    }

    @Test
    @DisplayName("파일을 못 읽으면 막는다 — 읽다 실패했을 때 통과시키지 않는다")
    void 못_읽으면_막는다() {
        MockMultipartFile 못읽는파일 = new MockMultipartFile("f", "a.jpg", "image/jpeg", JPEG_HEADER) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("읽을 수 없다");
            }
        };

        assertThat(FileType.of(못읽는파일)).isEmpty();
    }

    // ── 저장 이름 ───────────────────────────────────────────────

    @Test
    @DisplayName("확장자는 실제 형식에서 온다 — 사용자가 준 이름을 안 쓴다")
    void 확장자는_실제_형식에서_온다() {
        assertThat(FileType.JPEG.extension()).isEqualTo(".jpg");
        assertThat(FileType.PNG.extension()).isEqualTo(".png");
        assertThat(FileType.PDF.extension()).isEqualTo(".pdf");
    }

    @Test
    @DisplayName("앞 8바이트만 읽는다 — 10MB 를 통째로 안 올린다")
    void 앞부분만_읽는다() {
        읽은바이트 세는파일 = new 읽은바이트("a.pdf", PDF_HEADER);

        FileType.of(세는파일);

        // getBytes() 로 통째로 읽으면 동시에 올릴 때 메모리가 그만큼 쌓인다
        assertThat(세는파일.읽은양()).isLessThanOrEqualTo(8);
    }

    // ── 거들기 ─────────────────────────────────────────────────

    private static MockMultipartFile 파일(String name, String contentType, byte[] content) {
        return new MockMultipartFile("bizLicense", name, contentType, content);
    }

    /** 몇 바이트를 읽어 갔는지 센다. */
    private static class 읽은바이트 extends MockMultipartFile {

        private int 읽은양;

        읽은바이트(String name, byte[] content) {
            super("bizLicense", name, "application/pdf", content);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(getBytesQuietly()) {
                @Override
                public int read(byte[] b, int off, int len) {
                    int read = super.read(b, off, len);
                    if (read > 0) {
                        읽은양 += read;
                    }
                    return read;
                }
            };
        }

        private byte[] getBytesQuietly() {
            try {
                return super.getBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        int 읽은양() {
            return 읽은양;
        }
    }
}
