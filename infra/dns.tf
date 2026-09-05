# ① 호스팅존 — ddmondo.co.kr 의 DNS 장부
#
# 이걸 만들면 AWS 가 네임서버 4개를 배정해준다.
# 그 4개를 가비아 「네임서버 설정」에 넣어야 이 장부가 실제로 쓰인다.
resource "aws_route53_zone" "root" {
  name    = "ddmondo.co.kr"
  comment = "ondo - 가비아 등록 · Route 53 이 관리"
}

# ② pos.ddmondo.co.kr — 도매 화면 (Vercel)
#
# Vercel 이 프로젝트마다 다른 대상을 준다. 화면에 뜬 값을 그대로 넣는다.
# 다른 프로젝트에 같은 값을 쓰면 안 된다.
#
# 인증서는 우리가 안 만든다. Vercel 이 이 CNAME 을 보고 자기 것을 발급한다.
# ACM(api·api-dev)과 무관하다.
resource "aws_route53_record" "pos" {
  zone_id = aws_route53_zone.root.zone_id
  name    = "pos.ddmondo.co.kr"
  type    = "CNAME"
  ttl     = 300
  records = ["6b55998f3173ab8b.vercel-dns-017.com"]
}

# ③ ddmondo.co.kr — 소매 화면 On도마켓 (Vercel)
#
# 뿌리 도메인이라 CNAME 을 못 쓴다. 뿌리에는 이미 NS·SOA 가 박혀 있고
# CNAME 은 다른 레코드와 같이 못 있는다는 규칙 때문이다. 그래서 Vercel 이
# 준 IP 를 A 로 직접 박는다.
#
# ⚠ CNAME 과 달리 Vercel 이 IP 를 바꾸면 우리가 고쳐야 한다.
#   화면이 통째로 안 열리는 종류라, Vercel 이 바꾼다고 하면 바로 반영한다.
#
# 인증서는 Vercel 이 발급한다. ACM(api·api-dev)과 무관하다.
resource "aws_route53_record" "root" {
  zone_id = aws_route53_zone.root.zone_id
  name    = "ddmondo.co.kr"
  type    = "A"
  ttl     = 300
  records = ["216.198.79.1"]
}
