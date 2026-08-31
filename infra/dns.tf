# ① 호스팅존 — ddmondo.co.kr 의 DNS 장부
#
# 이걸 만들면 AWS 가 네임서버 4개를 배정해준다.
# 그 4개를 가비아 「네임서버 설정」에 넣어야 이 장부가 실제로 쓰인다.
resource "aws_route53_zone" "root" {
  name    = "ddmondo.co.kr"
  comment = "ondo - 가비아 등록 · Route 53 이 관리"
}
