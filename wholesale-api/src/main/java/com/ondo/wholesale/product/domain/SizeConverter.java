package com.ondo.wholesale.product.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link Size} ↔ DB varchar 변환.
 *
 * <p>{@code @Enumerated(STRING)} 은 상수명 {@code X2L} 을 내보내 DB CHECK(variant_size_ck,
 * '2XL' 허용)에 걸린다. JSON 과 같은 라벨('2XL')을 DB 에도 쓰기 위한 컨버터다.
 * autoApply 라 Size 필드는 별도 애노테이션 없이 전부 이 변환을 탄다.
 */
@Converter(autoApply = true)
public class SizeConverter implements AttributeConverter<Size, String> {

    @Override
    public String convertToDatabaseColumn(Size size) {
        return size == null ? null : size.label();
    }

    @Override
    public Size convertToEntityAttribute(String label) {
        return label == null ? null : Size.fromLabel(label);
    }
}
