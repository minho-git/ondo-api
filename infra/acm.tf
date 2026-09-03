# ② 인증서 요청 — api.ddmondo.co.kr 용 HTTPS 인증서
#
# ALB 와 같은 리전(ap-northeast-2)이어야 붙일 수 있다.
# 프론트(ddmondo.co.kr · pos.)는 Vercel 이 자기 인증서를 쓰므로 여기 안 넣는다.
resource "aws_acm_certificate" "api" {
  domain_name       = "api.ddmondo.co.kr"
  validation_method = "DNS"

  # 인증서를 새로 뽑을 때 옛것을 먼저 지우면 ALB 가 잠깐 인증서 없이 남는다
  lifecycle {
    create_before_destroy = true
  }
}

# ③ 검증 레코드 — ACM 이 "이 도메인 네 거 맞아?" 확인하려고 요구하는 CNAME
#
# ②를 가리키므로 인증서가 먼저 만들어지고,
# ①을 가리키므로 호스팅존도 먼저 만들어진다. 순서를 우리가 안 적어도 된다.
resource "aws_route53_record" "api_cert_validation" {
  # ACM 이 돌려준 목록을 그대로 도는 반복문.
  # 지금은 도메인이 하나라 한 바퀴지만, 나중에 도메인을 추가해도 코드를 안 고친다.
  for_each = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name  # key   ← 셋 다 ACM 이 준다
      type  = dvo.resource_record_type  # 타입
      value = dvo.resource_record_value # value
    }
  }

  zone_id = aws_route53_zone.root.zone_id
  name    = each.value.name
  type    = each.value.type
  records = [each.value.value]
  ttl     = 60 # 짧게 잡아야 발급이 빨리 된다

  allow_overwrite = true
}

# ④ 개발 환경용 인증서 — api-dev.ddmondo.co.kr
#
# 지금 올리는 환경이 쓴다. 위의 api 인증서는 손대지 않고 그대로 둔다 —
# 나중에 운영 환경을 만들 때 그게 쓰인다. 환경마다 자기 인증서를 갖는 구조다.
# ACM 인증서는 무료라 개수를 아낄 이유가 없다.
resource "aws_acm_certificate" "api_dev" {
  domain_name       = "api-dev.ddmondo.co.kr"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "api_dev_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.api_dev.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }

  zone_id = aws_route53_zone.root.zone_id
  name    = each.value.name
  type    = each.value.type
  records = [each.value.value]
  ttl     = 60

  allow_overwrite = true
}
