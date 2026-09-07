package com.ondo.wholesale.product.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@link Size#valueOf(String)} 오용 가드. DB 는 SizeConverter 가 적은 '2XL' 라벨을
 * 저장하는데 valueOf 는 enum 상수명(X2L)을 기대한다 — jdbc 조회마다 같은 실수가
 * 반복돼(주문·출고 조회에서 두 번) 빌드에서 막는다. 프로덕션·테스트를 다 훑는다.
 */
class SizeArchTest {

    @Test
    void Size_valueOf는_어디서도_부르지_않는다() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ondo.wholesale");
        noClasses()
                .should().callMethod(Size.class, "valueOf", String.class)
                .because("valueOf 는 enum 상수명(X2L)용이다 — DB 라벨('2XL')은 Size.fromLabel 을 써라")
                .check(classes);
    }
}
