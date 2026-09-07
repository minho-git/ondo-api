package com.ondo.retail.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 앞머리를 읽어 <b>진짜 형식</b>을 알아낸다 (MUL-99).
 *
 * <p>전에는 {@code Content-Type} 만 봤다. <b>그건 브라우저가 보내는 값이라 조작된다.</b>
 * 아무 파일에 {@code Content-Type: image/jpeg} 를 붙이면 그대로 통과했다(숙제 10번).
 *
 * <p>파일 형식은 대부분 <b>맨 앞 몇 바이트가 정해져 있다.</b> 그걸 매직 넘버라 부른다.
 * 이름을 바꾸든 헤더를 조작하든 이 값은 안 바뀐다 — 그게 실제로 그 형식이라는 뜻이다.
 *
 * <pre>
 *   JPEG  FF D8 FF
 *   PNG   89 50 4E 47 0D 0A 1A 0A      뒤 다섯은 전송 중 깨짐을 잡으려고 넣은 것
 *   PDF   25 50 44 46                  아스키로 "%PDF"
 * </pre>
 *
 * <p><b>이걸로 안전이 끝나는 건 아니다.</b> 진짜 JPEG 안에 나쁜 걸 숨기는 방법도 있다.
 * 다만 "확장자만 바꾼 실행 파일" 은 여기서 걸린다. 값싸게 막을 수 있는 걸 막는 것이다.
 */
public enum FileType {

    JPEG(".jpg", "image/jpeg", (byte) 0xFF, (byte) 0xD8, (byte) 0xFF),

    PNG(".png", "image/png",
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A),

    PDF(".pdf", "application/pdf", (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46);

    /** 판단에 필요한 만큼만 읽는다. 제일 긴 게 PNG 의 8바이트다. */
    private static final int HEADER_SIZE = 8;

    private final String extension;
    private final String contentType;
    private final byte[] magic;

    FileType(String extension, String contentType, byte... magic) {
        this.extension = extension;
        this.contentType = contentType;
        this.magic = magic;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 파일 앞머리를 보고 형식을 고른다.
     *
     * @return 아는 형식이 아니면 비어 있다. 빈 파일도 마찬가지다
     */
    public static Optional<FileType> of(MultipartFile file) {
        byte[] header = readHeader(file);
        return Arrays.stream(values())
                .filter(type -> type.matches(header))
                .findFirst();
    }

    private boolean matches(byte[] header) {
        if (header.length < magic.length) {
            return false;
        }
        return Arrays.equals(header, 0, magic.length, magic, 0, magic.length);
    }

    /**
     * 앞 8바이트만 읽는다.
     *
     * <p>{@code getBytes()} 로 통째로 읽지 않는 이유 — 10MB 짜리를 판단 하나 하자고
     * 통째로 메모리에 올릴 이유가 없다. 여러 명이 동시에 올리면 그게 다 쌓인다.
     *
     * <p>못 읽으면 빈 배열을 준다. 그러면 아는 형식이 하나도 안 맞아서 거절된다 —
     * <b>읽다 실패했을 때 통과시키는 것보다 막는 쪽이 낫다.</b>
     */
    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_SIZE);
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
